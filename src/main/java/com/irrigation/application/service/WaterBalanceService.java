package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.ReprocessWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.exception.PreconditionsNotMetException.Precondition;
import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.repository.AppliedIrrigationRepository;
import com.irrigation.domain.repository.AreaOperationLock;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.DailyWeatherRepository;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.domain.waterbalance.WaterBalanceCalculator;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso do cálculo do balanço hídrico diário (Req 7, 8, 9, 10, 11, 12 e 15.8).
 *
 * <p>Ordem das verificações do cálculo (Req 11.6, A-03 e A-06):
 * <ol>
 *   <li>formato (400): data obrigatória e TR inteiro maior ou igual a 1, todas as violações juntas;</li>
 *   <li>área (404);</li>
 *   <li>bloqueio da área (409 se outra operação a detém);</li>
 *   <li>plano da data: área sem plano (422), data anterior ao início do primeiro plano (400) e data fora
 *       de qualquer plano (422);</li>
 *   <li>balanço corrente já existente na data (422);</li>
 *   <li>pré-condições de dados, agregadas em uma única resposta 422: meteorologia, Irrigação Real
 *       (zero conta como presente), campos do solo e campos da cultura;</li>
 *   <li>lacuna de histórico (422 com a primeira data sem cálculo);</li>
 *   <li>cálculo pelo {@link WaterBalanceCalculator} e gravação como revisão 1 corrente.</li>
 * </ol>
 *
 * <p>O Primeiro_Dia_do_Plano é a data de início do plano que cobre a data calculada. Nesse dia AFDi = CAD × F;
 * nos demais, AFDi é a AFDf corrente do dia anterior, sem arredondamento. Os valores são gravados sem
 * arredondamento: o arredondamento de apresentação só ocorre ao montar {@link WaterBalanceOutput}.
 * Qualquer falha desfaz a transação, então nada parcial é mantido.
 */
@Service
public class WaterBalanceService {

    private static final Logger log = LoggerFactory.getLogger(WaterBalanceService.class);
    private static final String AREA = "Área";
    private static final int FIRST_REVISION = 1;

    private final AreaRepository areas;
    private final ManagementPlanRepository plans;
    private final SoilProfileRepository soilProfiles;
    private final CropRepository crops;
    private final DailyWeatherRepository weatherRepository;
    private final AppliedIrrigationRepository irrigationRepository;
    private final DailyWaterBalanceRepository balances;
    private final AreaOperationLock areaLock;
    private final WaterBalanceCalculator calculator;
    private final Clock clock;

    public WaterBalanceService(AreaRepository areas, ManagementPlanRepository plans,
                               SoilProfileRepository soilProfiles, CropRepository crops,
                               DailyWeatherRepository weatherRepository,
                               AppliedIrrigationRepository irrigationRepository,
                               DailyWaterBalanceRepository balances, AreaOperationLock areaLock,
                               Clock clock) {
        this.areas = areas;
        this.plans = plans;
        this.soilProfiles = soilProfiles;
        this.crops = crops;
        this.weatherRepository = weatherRepository;
        this.irrigationRepository = irrigationRepository;
        this.balances = balances;
        this.areaLock = areaLock;
        this.calculator = new WaterBalanceCalculator();
        this.clock = clock;
    }

    /** Calcula e grava o balanço hídrico da área na data, como revisão 1 corrente (Req 7.1 a 7.9). */
    @Transactional
    public WaterBalanceOutput calculate(UUID areaId, CalculateWaterBalanceInput input) {
        // 1. Formato (400)
        ViolationCollector v = new ViolationCollector();
        v.require("areaId", areaId);
        LocalDate date = null;
        CycleDay cycleDay = null;
        if (input == null) {
            v.add("date", "é obrigatório");
            v.add("cycleDay", "é obrigatório");
        } else {
            date = v.require("date", input.date());
            cycleDay = v.build("cycleDay", input.cycleDay(), CycleDay::new);
        }
        v.throwIfAny();

        // 2. Área (404)
        if (!areas.existsById(areaId)) {
            throw new EntityNotFoundException(AREA, areaId);
        }

        // 3. Bloqueio da área (409)
        if (!areaLock.tryLock(areaId)) {
            throw new ConflictException("Já existe um cálculo ou reprocessamento em andamento para a área "
                    + areaId + ". Tente novamente em instantes");
        }

        // 4. Plano da data (A-03)
        ManagementPlan plan = resolvePlan(areaId, date);

        // 5. Duplicidade (Req 11.7)
        if (balances.findCurrent(areaId, date).isPresent()) {
            throw new BusinessRuleException("A data " + date + " já foi calculada para a área " + areaId
                    + ". Use o reprocessamento para recalcular");
        }

        // 6. Pré-condições de dados, agregadas (Req 11.1 a 11.5)
        DayData data = loadDayData(areaId, date, plan);
        SoilProfile soil = data.soil();
        Crop crop = data.crop();

        // 7. Histórico (Req 7.8 e 8.4)
        boolean firstDayOfPlan = date.equals(plan.getStartDate());
        BigDecimal previousAfdFinal = null;
        if (!firstDayOfPlan) {
            LocalDate previousDay = date.minusDays(1);
            Optional<LocalDate> gap = balances.firstMissingDate(areaId, plan.getStartDate(), previousDay);
            if (gap.isPresent()) {
                throw new BusinessRuleException("Não há balanço calculado para a data " + gap.get()
                        + ". Calcule os dias anteriores em ordem a partir do início do plano ("
                        + plan.getStartDate() + ")");
            }
            previousAfdFinal = balances.findCurrent(areaId, previousDay)
                    .map(b -> b.result().afdFinal())
                    .orElseThrow(() -> new BusinessRuleException("Não há balanço calculado para a data "
                            + previousDay));
        }

        // 8. Cálculo e gravação
        WaterBalanceInputs inputs = buildInputs(data, plan, firstDayOfPlan, previousAfdFinal);
        WaterBalanceResult result = calculator.calculate(inputs);

        Instant calculatedAt = clock.instant();
        DailyWaterBalance saved = balances.insert(new DailyWaterBalance(
                UUID.randomUUID(), areaId, date, crop.getId(), soil.getId(), plan.getId(), cycleDay,
                inputs, result, calculatedAt,
                WaterBalanceCalculator.METHODOLOGY_ID, WaterBalanceCalculator.METHODOLOGY_VERSION,
                FIRST_REVISION, true));
        log.info("Balanço hídrico calculado: areaId={}, date={}, recommended={}",
                areaId, date, result.recommended());
        return WaterBalanceOutput.from(saved);
    }

    /**
     * Reprocessa a cadeia de balanços a partir da data inicial, tudo-ou-nada (Req 13, DD-02).
     *
     * <p>Ordem: formato (400), área (404), bloqueio (409), data inicial com revisão corrente (422).
     * Calcula em memória a data inicial e todas as datas posteriores que já têm revisão corrente, com
     * cadastros, meteorologia e Irrigação Real atuais. Qualquer pré-condição violada aborta com 422 e a
     * data violadora, sem gravar nada. Em sucesso, na mesma transação, marca as correntes como não
     * correntes e insere as revisões n+1. Dias nunca calculados não são calculados.
     *
     * @return as novas revisões, em ordem cronológica
     */
    @Transactional
    public List<WaterBalanceOutput> reprocess(UUID areaId, ReprocessWaterBalanceInput input) {
        // 1. Formato (400)
        ViolationCollector v = new ViolationCollector();
        v.require("areaId", areaId);
        LocalDate startDate = null;
        if (input == null) {
            v.add("date", "é obrigatório");
        } else {
            startDate = v.require("date", input.date());
        }
        v.throwIfAny();

        // 2. Área (404)
        if (!areas.existsById(areaId)) {
            throw new EntityNotFoundException(AREA, areaId);
        }

        // 3. Bloqueio da área (409)
        if (!areaLock.tryLock(areaId)) {
            throw new ConflictException("Já existe um cálculo ou reprocessamento em andamento para a área "
                    + areaId + ". Tente novamente em instantes");
        }

        // 4. Data inicial com revisão corrente (Req 13.7)
        List<DailyWaterBalance> chain = balances.findCurrentFrom(areaId, startDate);
        if (chain.isEmpty() || !chain.get(0).date().equals(startDate)) {
            throw new BusinessRuleException("A data " + startDate + " não foi calculada para a área " + areaId
                    + ". O reprocessamento exige uma data com balanço corrente");
        }

        // 5. Cadeia inteira em memória (Req 13.1 a 13.4)
        Instant calculatedAt = clock.instant();
        List<DailyWaterBalance> revisions = new ArrayList<>(chain.size());
        DailyWaterBalance previousNew = null;
        for (DailyWaterBalance old : chain) {
            LocalDate date = old.date();
            ManagementPlan plan = resolvePlan(areaId, date);
            DayData data = loadDayData(areaId, date, plan);

            boolean firstDayOfPlan = date.equals(plan.getStartDate());
            BigDecimal previousAfdFinal = null;
            if (!firstDayOfPlan) {
                LocalDate previousDay = date.minusDays(1);
                if (previousNew != null && previousNew.date().equals(previousDay)) {
                    previousAfdFinal = previousNew.result().afdFinal();
                } else if (previousNew == null) {
                    previousAfdFinal = balances.findCurrent(areaId, previousDay)
                            .map(b -> b.result().afdFinal())
                            .orElseThrow(() -> new BusinessRuleException("Não há balanço calculado para a data "
                                    + previousDay + ", necessário para reprocessar a partir de " + date));
                } else {
                    throw new BusinessRuleException("Não há balanço calculado para a data " + previousDay
                            + ", necessário para reprocessar a data " + date);
                }
            }

            WaterBalanceInputs inputs = buildInputs(data, plan, firstDayOfPlan, previousAfdFinal);
            WaterBalanceResult result = calculator.calculate(inputs);
            previousNew = new DailyWaterBalance(
                    UUID.randomUUID(), areaId, date, data.crop().getId(), data.soil().getId(), plan.getId(),
                    old.cycleDay(), inputs, result, calculatedAt,
                    WaterBalanceCalculator.METHODOLOGY_ID, WaterBalanceCalculator.METHODOLOGY_VERSION,
                    old.revision() + 1, true);
            revisions.add(previousNew);
        }

        // 6. Gravação: marca as correntes como não correntes e insere as novas, na mesma transação
        balances.markNotCurrent(areaId, startDate);
        List<DailyWaterBalance> saved = balances.insertAll(revisions);
        log.info("Balanço hídrico reprocessado: areaId={}, from={}, days={}", areaId, startDate, saved.size());
        return saved.stream().map(WaterBalanceOutput::from).toList();
    }

    /** Dados atuais de um dia, já validados contra as pré-condições do Req 11. */
    private record DayData(DailyWeather weather, AppliedIrrigation irrigation, SoilProfile soil, Crop crop) {
    }

    /**
     * Carrega meteorologia, Irrigação Real, solo e cultura do plano e verifica as pré-condições de dados
     * (Req 11.1 a 11.5), agregando todas as faltas em uma única {@link PreconditionsNotMetException}.
     */
    private DayData loadDayData(UUID areaId, LocalDate date, ManagementPlan plan) {
        Optional<DailyWeather> weather = weatherRepository.findByAreaAndDate(areaId, date);
        Optional<AppliedIrrigation> irrigation = irrigationRepository.findByAreaAndDate(areaId, date);
        SoilProfile soil = soilProfiles.findById(plan.getSoilProfileId())
                .orElseThrow(() -> new EntityNotFoundException("Solo", plan.getSoilProfileId()));
        Crop crop = crops.findById(plan.getCropId())
                .orElseThrow(() -> new EntityNotFoundException("Cultura", plan.getCropId()));

        List<Precondition> missing = new ArrayList<>();
        if (weather.isEmpty()) {
            missing.add(new Precondition(Code.WEATHER_MISSING,
                    "Não há meteorologia diária (ETP e precipitação) da área na data " + date));
        }
        if (irrigation.isEmpty()) {
            missing.add(new Precondition(Code.APPLIED_IRRIGATION_MISSING,
                    "Não há Irrigação Real da área na data " + date));
        }
        List<String> soilFields = new ArrayList<>();
        if (soil.getFieldCapacityGravimetric() == null) {
            soilFields.add("fieldCapacityGravimetric");
        }
        if (soil.getPermanentWiltingPointGravimetric() == null) {
            soilFields.add("permanentWiltingPointGravimetric");
        }
        if (soil.getBulkDensity() == null) {
            soilFields.add("bulkDensity");
        }
        if (!soilFields.isEmpty()) {
            missing.add(new Precondition(Code.SOIL_FIELDS_MISSING,
                    "O solo do plano não tem valor para: " + String.join(", ", soilFields), soilFields));
        }
        List<String> cropFields = new ArrayList<>();
        if (crop.getKc() == null) {
            cropFields.add("kc");
        }
        if (crop.getRootDepth() == null) {
            cropFields.add("rootDepth");
        }
        if (!cropFields.isEmpty()) {
            missing.add(new Precondition(Code.CROP_FIELDS_MISSING,
                    "A cultura do plano não tem valor para: " + String.join(", ", cropFields), cropFields));
        }
        if (!missing.isEmpty()) {
            throw new PreconditionsNotMetException(date, missing);
        }
        return new DayData(weather.get(), irrigation.get(), soil, crop);
    }

    /** Monta as entradas do calculador a partir dos dados do dia e do plano. */
    private static WaterBalanceInputs buildInputs(DayData data, ManagementPlan plan, boolean firstDayOfPlan,
                                                  BigDecimal previousAfdFinal) {
        return new WaterBalanceInputs(
                data.weather().getEtp(),
                data.weather().getPrecipitation(),
                data.irrigation().getDepth(),
                data.soil().getFieldCapacityGravimetric(),
                data.soil().getPermanentWiltingPointGravimetric(),
                data.soil().getBulkDensity(),
                data.crop().getRootDepth(),
                data.crop().getKc(),
                plan.getAvailabilityFactor(),
                plan.getSprinklerRate(),
                firstDayOfPlan,
                previousAfdFinal);
    }

    /**
     * Resolve o plano que cobre a data. Área sem plano: 422. Data anterior ao início do primeiro plano:
     * 400 (A-03). Data fora de qualquer plano: 422.
     */
    private ManagementPlan resolvePlan(UUID areaId, LocalDate date) {
        List<ManagementPlan> all = plans.findByAreaOrderByStartDateDesc(areaId);
        if (all.isEmpty()) {
            throw new BusinessRuleException("A área " + areaId + " não possui plano de manejo");
        }
        LocalDate firstStart = all.get(all.size() - 1).getStartDate();
        if (date.isBefore(firstStart)) {
            ViolationCollector v = new ViolationCollector();
            v.add("date", "não pode ser anterior ao início do primeiro plano de manejo da área (" + firstStart + ")");
            v.throwIfAny();
        }
        return plans.findCoveringDate(areaId, date)
                .orElseThrow(() -> new BusinessRuleException("A área " + areaId
                        + " não possui plano de manejo que cubra a data " + date));
    }
}
