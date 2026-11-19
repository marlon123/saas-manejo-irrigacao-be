package com.irrigation.application.service;

import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de planos de manejo (Req 4 do módulo de balanço hídrico).
 *
 * <p>Ordem das verificações (Req 11.6): validação de formato (400), existência (404) e regras de negócio (422).
 *
 * <p>Atualizar F ou a taxa não altera balanços já gravados: eles guardam o snapshot dos parâmetros usados
 * (Req 4.9). Só cálculos iniciados depois da confirmação da atualização leem os novos valores.
 *
 * <p>Na atualização, {@code cropId}, {@code soilProfileId} e {@code startDate} nulos significam "manter".
 * Informar um valor diferente do atual é uma troca: recusada com 422 se o plano tiver balanço (Req 4.10).
 * Sem balanço, cultura e solo podem ser trocados. A data de início nunca muda depois da criação.
 */
@Service
public class ManagementPlanService {

    private static final Logger log = LoggerFactory.getLogger(ManagementPlanService.class);
    private static final String AREA = "Área";
    private static final String CROP = "Cultura";
    private static final String SOIL = "Solo";
    private static final String PLAN = "Plano de manejo";

    private final ManagementPlanRepository plans;
    private final AreaRepository areas;
    private final CropRepository crops;
    private final SoilProfileRepository soilProfiles;

    public ManagementPlanService(ManagementPlanRepository plans, AreaRepository areas,
                                 CropRepository crops, SoilProfileRepository soilProfiles) {
        this.plans = plans;
        this.areas = areas;
        this.crops = crops;
        this.soilProfiles = soilProfiles;
    }

    /** Cria um plano ativo para a área (Req 4.2). */
    @Transactional
    public ManagementPlanOutput create(UUID areaId, ManagementPlanInput input) {
        // 400: formato e campos obrigatórios, todas as violações juntas (Req 4.3 a 4.5)
        ViolationCollector v = new ViolationCollector();
        v.require("areaId", areaId);
        UUID cropId = v.require("cropId", input.cropId());
        UUID soilId = v.require("soilProfileId", input.soilProfileId());
        LocalDate startDate = v.require("startDate", input.startDate());
        AvailabilityFactor factor = v.build("fFactor", input.fFactor(), AvailabilityFactor::new);
        SprinklerRate rate = v.build("sprinklerRateMmPerHour", input.sprinklerRateMmPerHour(), SprinklerRate::new);
        if (startDate != null) {
            v.check("endDate", () -> ManagementPlan.validateEndDate(startDate, input.endDate()));
        }
        v.throwIfAny();

        // 404: área, cultura e solo, com a mensagem identificando qual faltou (Req 4.6)
        requireArea(areaId);
        requireCrop(cropId);
        requireSoil(soilId);

        // 422: um plano ativo por área e sem sobreposição de períodos (Req 4.7, 4.8 e A-02)
        List<ManagementPlan> existing = plans.findByAreaOrderByStartDateDesc(areaId);
        if (input.endDate() == null && existing.stream().anyMatch(ManagementPlan::isActive)) {
            throw new BusinessRuleException("A área " + areaId + " já possui um plano de manejo ativo. "
                    + "Encerre-o antes de criar outro");
        }
        checkNoOverlap(existing, null, startDate, input.endDate());

        ManagementPlan saved = plans.save(new ManagementPlan(UUID.randomUUID(), areaId, cropId, soilId,
                startDate, input.endDate(), factor, rate));
        log.info("Plano de manejo criado: id={}, areaId={}", saved.getId(), areaId);
        return ManagementPlanOutput.from(saved);
    }

    /** Atualiza F, taxa e data final (encerramento) do plano (Req 4.9, 4.10 e A-02). */
    @Transactional
    public ManagementPlanOutput update(UUID areaId, UUID planId, ManagementPlanInput input) {
        // 400
        ViolationCollector v = new ViolationCollector();
        v.require("areaId", areaId);
        v.require("planId", planId);
        AvailabilityFactor factor = v.build("fFactor", input.fFactor(), AvailabilityFactor::new);
        SprinklerRate rate = v.build("sprinklerRateMmPerHour", input.sprinklerRateMmPerHour(), SprinklerRate::new);
        v.throwIfAny();

        // 404: área, plano da área e, se informados, cultura e solo
        requireArea(areaId);
        ManagementPlan current = plans.findById(planId)
                .filter(p -> p.getAreaId().equals(areaId))
                .orElseThrow(() -> new EntityNotFoundException(PLAN, planId));
        UUID cropId = input.cropId() != null ? input.cropId() : current.getCropId();
        UUID soilId = input.soilProfileId() != null ? input.soilProfileId() : current.getSoilProfileId();
        boolean changesCrop = !cropId.equals(current.getCropId());
        boolean changesSoil = !soilId.equals(current.getSoilProfileId());
        if (changesCrop) {
            requireCrop(cropId);
        }
        if (changesSoil) {
            requireSoil(soilId);
        }

        // Com a data de início conhecida, a data final é validada em formato (400) antes das regras de negócio.
        LocalDate endDate = input.endDate();
        ViolationCollector endCheck = new ViolationCollector();
        endCheck.check("endDate", () -> ManagementPlan.validateEndDate(current.getStartDate(), endDate));
        endCheck.throwIfAny();

        // 422
        if (input.startDate() != null && !input.startDate().equals(current.getStartDate())) {
            throw new BusinessRuleException("A data de início do plano não pode ser alterada. "
                    + "Encerre o plano e crie outro");
        }
        if ((changesCrop || changesSoil) && plans.hasBalances(planId)) {
            throw new BusinessRuleException("O plano " + planId + " já possui balanços hídricos calculados: "
                    + "não é possível trocar a cultura nem o solo. Encerre o plano e crie outro");
        }
        if (endDate != null) {
            Optional<LocalDate> last = plans.lastCalculatedDate(planId);
            if (last.isPresent() && endDate.isBefore(last.get())) {
                throw new BusinessRuleException("A data final " + endDate + " é anterior ao último dia calculado do plano ("
                        + last.get() + ")");
            }
        }
        List<ManagementPlan> others = plans.findByAreaOrderByStartDateDesc(areaId);
        if (endDate == null && !current.isActive()
                && others.stream().anyMatch(p -> !p.getId().equals(planId) && p.isActive())) {
            throw new BusinessRuleException("A área " + areaId + " já possui um plano de manejo ativo");
        }
        checkNoOverlap(others, planId, current.getStartDate(), endDate);

        ManagementPlan updated = new ManagementPlan(current.getId(), areaId, cropId, soilId,
                current.getStartDate(), endDate, factor, rate);
        ManagementPlan saved = plans.save(updated);
        log.info("Plano de manejo atualizado: id={}, areaId={}", saved.getId(), areaId);
        return ManagementPlanOutput.from(saved);
    }

    /** Planos da área: ativo e encerrados, por início decrescente; lista vazia sem planos (Req 4.11 e 4.12). */
    @Transactional(readOnly = true)
    public List<ManagementPlanOutput> listByArea(UUID areaId) {
        requireArea(areaId);
        return plans.findByAreaOrderByStartDateDesc(areaId).stream().map(ManagementPlanOutput::from).toList();
    }

    private void requireArea(UUID id) {
        if (!areas.existsById(id)) {
            throw new EntityNotFoundException(AREA, id);
        }
    }

    private void requireCrop(UUID id) {
        if (!crops.existsById(id)) {
            throw new EntityNotFoundException(CROP, id);
        }
    }

    private void requireSoil(UUID id) {
        if (!soilProfiles.existsById(id)) {
            throw new EntityNotFoundException(SOIL, id);
        }
    }

    /** Dois períodos se sobrepõem se um começa antes do fim do outro (data final nula = aberto). */
    private void checkNoOverlap(List<ManagementPlan> existing, UUID ignoredPlanId,
                                LocalDate start, LocalDate end) {
        for (ManagementPlan other : existing) {
            if (other.getId().equals(ignoredPlanId)) {
                continue;
            }
            boolean startsBeforeOtherEnds = other.getEndDate() == null || !start.isAfter(other.getEndDate());
            boolean endsAfterOtherStarts = end == null || !end.isBefore(other.getStartDate());
            if (startsBeforeOtherEnds && endsAfterOtherStarts) {
                throw new BusinessRuleException("O período do plano se sobrepõe ao plano " + other.getId()
                        + " (" + other.getStartDate() + " a "
                        + (other.getEndDate() == null ? "em aberto" : other.getEndDate().toString()) + ")");
            }
        }
    }
}
