package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.exception.PreconditionsNotMetException.Precondition;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.waterbalance.NoRecommendationReason;
import com.irrigation.domain.waterbalance.WaterBalanceCalculator;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository.InjectedFailureException;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static com.irrigation.support.TestData.scenarioDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários do cálculo diário de {@link WaterBalanceService#calculate} (Req 7, 8, 11, 12 e 15),
 * com os Cenários A, B e D de requirements.md. O reprocessamento fica em {@link WaterBalanceReprocessTest}.
 */
class WaterBalanceServiceTest {

    private WaterBalanceServiceHarness h;

    @BeforeEach
    void setUp() {
        h = WaterBalanceServiceHarness.scenarioA(7);
    }

    private static CalculateWaterBalanceInput request(int dayNumber, Integer cycleDay) {
        return new CalculateWaterBalanceInput(scenarioDate(dayNumber), cycleDay);
    }

    private static List<Code> codes(PreconditionsNotMetException e) {
        return e.getPreconditions().stream().map(Precondition::code).toList();
    }

    private PreconditionsNotMetException preconditionsFailure(int dayNumber) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> h.calculateDay(dayNumber));
        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        return (PreconditionsNotMetException) thrown;
    }

    // ------------------------------------------------------------ Ordem das verificações

    @Test
    @DisplayName("Req 11.6/11.8: formato inválido acumula todas as violações e não consulta área nem bloqueio")
    void invalidFormatAccumulatesViolationsBeforeAnyLookup() {
        UUID unknownArea = UUID.randomUUID();

        assertThatThrownBy(() -> h.service.calculate(unknownArea, new CalculateWaterBalanceInput(null, 0)))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getViolations())
                        .extracting(FieldViolation::field)
                        .containsExactlyInAnyOrder("date", "cycleDay"));

        assertThat(h.lock.callCount()).isZero();
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 7.4: TR nulo e corpo nulo são 400")
    void nullCycleDayAndNullBodyAreRejected() {
        assertThatThrownBy(() -> h.service.calculate(h.areaId, request(1, null)))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getViolations())
                        .extracting(FieldViolation::field).contains("cycleDay"));
        assertThatThrownBy(() -> h.service.calculate(h.areaId, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 7.5: área inexistente é 404, antes do bloqueio e das pré-condições")
    void unknownAreaIsNotFoundBeforeLockOrPreconditions() {
        h.lock.setAvailable(false);

        assertThatThrownBy(() -> h.service.calculate(UUID.randomUUID(), request(1, 1)))
                .isInstanceOf(EntityNotFoundException.class);

        assertThat(h.lock.callCount()).isZero();
    }

    @Test
    @DisplayName("Req 13.8: bloqueio indisponível é 409, antes de plano e pré-condições, sem gravar")
    void unavailableLockIsConflictBeforePlanAndPreconditions() {
        h.lock.setAvailable(false);
        h.weather.hide(scenarioDate(1));

        assertThatThrownBy(() -> h.calculateDay(1)).isInstanceOf(ConflictException.class);

        assertThat(h.lock.calls()).containsExactly(h.areaId);
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Ordem 400, 404, 409 e 422: cada etapa só é alcançada quando as anteriores passam")
    void checksRunInTheDocumentedOrder() {
        h.weather.hide(scenarioDate(1)); // 422 pendente em todos os casos abaixo
        UUID unknownArea = UUID.randomUUID();
        h.lock.setAvailable(false);

        // 400 antes de 404
        assertThatThrownBy(() -> h.service.calculate(unknownArea, request(1, 0)))
                .isInstanceOf(ValidationFailedException.class);
        // 404 antes de 409
        assertThatThrownBy(() -> h.service.calculate(unknownArea, request(1, 1)))
                .isInstanceOf(EntityNotFoundException.class);
        // 409 antes de 422
        assertThatThrownBy(() -> h.calculateDay(1)).isInstanceOf(ConflictException.class);
        // 422 quando o bloqueio é obtido
        h.lock.setAvailable(true);
        assertThatThrownBy(() -> h.calculateDay(1)).isInstanceOf(PreconditionsNotMetException.class);
        assertThat(h.balances.count()).isZero();
    }

    // ------------------------------------------------------------ Plano (A-03)

    @Test
    @DisplayName("Req 7.6: área sem plano de manejo é 422, sem gravar")
    void areaWithoutPlanIsUnprocessable() {
        UUID otherArea = UUID.randomUUID();
        h.areas.save(TestData.area(otherArea, "Sem plano", "5.00"));

        assertThatThrownBy(() -> h.service.calculate(otherArea, request(1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(PreconditionsNotMetException.class)
                .hasMessageContaining("plano");
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("A-03: data anterior ao início do primeiro plano é 400 no campo date")
    void dateBeforeFirstPlanIsBadRequest() {
        LocalDate before = TestData.SCENARIO_START.minusDays(1);

        assertThatThrownBy(() -> h.service.calculate(h.areaId, new CalculateWaterBalanceInput(before, 1)))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((ValidationFailedException) e).getViolations())
                        .extracting(FieldViolation::field).containsExactly("date"));
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("A-03: data posterior ao fim do plano, sem outro plano que a cubra, é 422")
    void dateNotCoveredByAnyPlanIsUnprocessable() {
        h.plan.close(scenarioDate(3));

        assertThatThrownBy(() -> h.calculateDay(5))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(PreconditionsNotMetException.class)
                .hasMessageContaining(scenarioDate(5).toString());
        assertThat(h.balances.count()).isZero();
    }

    // ------------------------------------------------------------ Duplicidade e histórico

    @Test
    @DisplayName("Req 11.7 e Cenário D: calcular a mesma área e data duas vezes é 422 e mantém uma linha corrente")
    void duplicateDateIsRejectedAndKeepsTheCurrentRecord() {
        WaterBalanceOutput first = h.calculateDay(1);

        assertThatThrownBy(() -> h.calculateDay(1))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(PreconditionsNotMetException.class)
                .hasMessageContaining(scenarioDate(1).toString());

        assertThat(h.balances.count()).isEqualTo(1);
        assertThat(h.current(1).id()).isEqualTo(first.id());
        assertThat(h.current(1).revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Req 11.7: a duplicidade é verificada antes das pré-condições de dados")
    void duplicateIsCheckedBeforeDataPreconditions() {
        h.calculateDay(1);
        h.weather.hide(scenarioDate(1));

        assertThatThrownBy(() -> h.calculateDay(1))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(PreconditionsNotMetException.class);
    }

    @Test
    @DisplayName("Req 7.8 e Cenário D: dia 3 sem o dia 2 calculado é 422 com a primeira data sem cálculo")
    void gapInHistoryReportsTheFirstMissingDate() {
        h.calculateDay(1);

        assertThatThrownBy(() -> h.calculateDay(3))
                .isInstanceOf(BusinessRuleException.class)
                .isNotInstanceOf(PreconditionsNotMetException.class)
                .hasMessageContaining(scenarioDate(2).toString());

        assertThat(h.balances.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Req 8.4: sem nenhum dia anterior, a primeira data sem cálculo é o início do plano")
    void gapReportsThePlanStartWhenNothingWasCalculated() {
        assertThatThrownBy(() -> h.calculateDay(2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(scenarioDate(1).toString());
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 7.8: com os dias 1 e 2 calculados, o dia 4 aponta o dia 3 como primeira lacuna")
    void gapReportsTheFirstOfSeveralMissingDays() {
        h.calculateDays(2);

        assertThatThrownBy(() -> h.calculateDay(4))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(scenarioDate(3).toString());
        assertThat(h.balances.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Req 11.6: pré-condições de dados são verificadas antes da lacuna de histórico")
    void dataPreconditionsComeBeforeTheHistoryGap() {
        h.weather.hide(scenarioDate(3));

        PreconditionsNotMetException e = preconditionsFailure(3);

        assertThat(codes(e)).containsExactly(Code.WEATHER_MISSING);
    }

    // ------------------------------------------------------------ Pré-condições de dados

    @Test
    @DisplayName("Req 11.1 e Cenário D: sem meteorologia é 422 WEATHER_MISSING com a data, nada persistido")
    void missingWeatherIsReportedAlone() {
        h.weather.hide(scenarioDate(1));

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.WEATHER_MISSING);
        assertThat(e.getDate()).isEqualTo(scenarioDate(1));
        assertThat(e.getPreconditions().get(0).message()).contains(scenarioDate(1).toString());
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 11.2 e Cenário D: sem Irrigação Real é 422 APPLIED_IRRIGATION_MISSING com a data")
    void missingAppliedIrrigationIsReportedAlone() {
        h.irrigation.hide(scenarioDate(1));

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.APPLIED_IRRIGATION_MISSING);
        assertThat(e.getPreconditions().get(0).message()).contains(scenarioDate(1).toString());
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 11.2: Irrigação Real com lâmina zero conta como presente")
    void zeroAppliedIrrigationCountsAsPresent() {
        assertThat(TestData.SCENARIO_A_IRRIGATION.get(0)).isEqualTo("0");

        WaterBalanceOutput out = h.calculateDay(1);

        assertThat(out.inputs().appliedIrrigationMm()).isEqualByComparingTo("0");
        assertThat(h.balances.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Req 11.3 e Cenário D: solo sem CC e PMP gravimétricos lista os dois campos")
    void missingSoilFieldsAreListedByName() {
        h.replaceSoil(null, null, "1.2");

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.SOIL_FIELDS_MISSING);
        assertThat(e.getPreconditions().get(0).fields())
                .containsExactly("fieldCapacityGravimetric", "permanentWiltingPointGravimetric");
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 11.4: cultura sem KC e sem Z lista os dois campos")
    void missingCropFieldsAreListedByName() {
        h.replaceCrop(null, null);

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.CROP_FIELDS_MISSING);
        assertThat(e.getPreconditions().get(0).fields()).containsExactly("kc", "rootDepth");
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 11.4: cultura sem Z lista só rootDepth")
    void cropWithoutRootDepthListsOnlyThatField() {
        h.replaceCrop("0.8", null);

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(e.getPreconditions().get(0).fields()).containsExactly("rootDepth");
    }

    @Test
    @DisplayName("Req 11.5: as quatro pré-condições juntas viram uma única resposta, sem omissão nem repetição")
    void allPreconditionsAreAggregatedInOneFailure() {
        h.weather.hide(scenarioDate(1));
        h.irrigation.hide(scenarioDate(1));
        h.replaceSoil(null, null, "1.2");
        h.replaceCrop(null, null);

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.WEATHER_MISSING, Code.APPLIED_IRRIGATION_MISSING,
                Code.SOIL_FIELDS_MISSING, Code.CROP_FIELDS_MISSING);
        assertThat(e.getPreconditions()).doesNotHaveDuplicates();
        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 11.5: duas pré-condições (meteorologia e cultura) geram duas entradas")
    void twoPreconditionsGenerateTwoEntries() {
        h.weather.hide(scenarioDate(1));
        h.replaceCrop("0.8", null);

        PreconditionsNotMetException e = preconditionsFailure(1);

        assertThat(codes(e)).containsExactly(Code.WEATHER_MISSING, Code.CROP_FIELDS_MISSING);
    }

    // ------------------------------------------------------------ Persistência

    @Test
    @DisplayName("Req 7.2 e 12.5: falha na gravação propaga o erro e não deixa registro parcial")
    void persistenceFailureLeavesNothing() {
        h.balances.failOnInsertNumber(1);

        assertThatThrownBy(() -> h.calculateDay(1)).isInstanceOf(InjectedFailureException.class);

        assertThat(h.balances.count()).isZero();
    }

    @Test
    @DisplayName("Req 12.6 e Cenário D: F, solo e cultura alterados depois do cálculo não mudam o balanço gravado")
    void registrySnapshotIsNotAffectedByLaterChanges() {
        WaterBalanceOutput before = h.calculateDay(1);
        DailyWaterBalance stored = h.current(1);

        h.plan.updateParameters(new AvailabilityFactor(bd("0.90")), new SprinklerRate(bd("3")));
        h.replaceSoil("40", "10", "1.5");
        h.replaceCrop("1.2", "800");

        DailyWaterBalance after = h.current(1);
        assertThat(after).isEqualTo(stored);
        WaterBalanceOutput read = h.query.findByDate(h.areaId, scenarioDate(1));
        assertThat(read.inputs().fFactor()).isEqualByComparingTo("0.50");
        assertThat(read.inputs().sprinklerRateMmPerHour()).isEqualByComparingTo("6");
        assertThat(read.inputs().fieldCapacityPct()).isEqualByComparingTo("30");
        assertThat(read.inputs().kc()).isEqualByComparingTo("0.8");
        assertThat(read.results().afdInitialMm()).isEqualByComparingTo(before.results().afdInitialMm());
        assertThat(read.results().cadMm()).isEqualByComparingTo("72");
    }

    // ------------------------------------------------------------ Cenário A

    @Test
    @DisplayName("Cenário A: 7 dias pelo serviço, valores persistidos coincidem com a tabela sem arredondamento")
    void scenarioAFullThroughTheService() {
        boolean[] recommended = {false, true, false, true, false, true, true};
        String[] hoursAndMinutes = {"0:0", "0:50", "0:0", "0:50", "0:0", "0:40", "2:0"};
        String[] etc = {"4", "5", "6", "5", "8", "4", "12"};

        for (int day = 1; day <= 7; day++) {
            int i = day - 1;
            WaterBalanceOutput out = h.calculateDay(day);
            DailyWaterBalance stored = h.current(day);

            assertThat(out.id()).isEqualTo(stored.id());
            assertThat(stored.date()).isEqualTo(scenarioDate(day));
            assertThat(stored.cycleDay().value()).isEqualTo(TestData.SCENARIO_A_CYCLE_DAYS.get(i));
            assertThat(stored.managementPlanId()).isEqualTo(h.plan.getId());
            assertThat(stored.cropId()).isEqualTo(h.crop.getId());
            assertThat(stored.soilProfileId()).isEqualTo(h.soil.getId());

            // valores persistidos, sem arredondamento
            assertThat(stored.result().cad()).isEqualByComparingTo("72");
            assertThat(stored.result().etc()).isEqualByComparingTo(etc[i]);
            assertThat(stored.result().afdInitial()).isEqualByComparingTo(TestData.SCENARIO_A_AFD_INITIAL.get(i));
            assertThat(stored.result().afdFinal()).isEqualByComparingTo(TestData.SCENARIO_A_AFD_FINAL.get(i));
            assertThat(stored.result().idealIrrigation())
                    .isEqualByComparingTo(TestData.SCENARIO_A_IDEAL_IRRIGATION.get(i));
            assertThat(stored.result().recommended()).isEqualTo(recommended[i]);
            BigDecimal expectedHours = recommended[i]
                    ? bd(TestData.SCENARIO_A_IDEAL_IRRIGATION.get(i)).divide(bd("6"), 10, RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO;
            assertThat(stored.result().operatingTimeHours()).isEqualByComparingTo(expectedHours);
            assertThat(stored.result().noRecommendationReason())
                    .isEqualTo(day == 1 ? NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE : null);

            // controle
            assertThat(stored.revision()).isEqualTo(1);
            assertThat(stored.current()).isTrue();
            assertThat(stored.methodologyId()).isEqualTo(WaterBalanceCalculator.METHODOLOGY_ID);
            assertThat(stored.methodologyVersion()).isEqualTo(WaterBalanceCalculator.METHODOLOGY_VERSION);
            assertThat(stored.calculatedAt()).isEqualTo(TestData.fixedClock().instant());

            // entradas gravadas
            assertThat(stored.inputs().etp().value()).isEqualByComparingTo(TestData.SCENARIO_A_ETP.get(i));
            assertThat(stored.inputs().precipitation().value())
                    .isEqualByComparingTo(TestData.SCENARIO_A_PRECIPITATION.get(i));
            assertThat(stored.inputs().appliedIrrigation().value())
                    .isEqualByComparingTo(TestData.SCENARIO_A_IRRIGATION.get(i));
            assertThat(stored.inputs().availabilityFactor().value()).isEqualByComparingTo("0.5");
            assertThat(stored.inputs().firstDayOfPlan()).isEqualTo(day == 1);

            // resposta (apresentação)
            String[] hm = hoursAndMinutes[i].split(":");
            assertThat(out.results().operatingTime().wholeHours()).isEqualTo(Integer.parseInt(hm[0]));
            assertThat(out.results().operatingTime().minutes()).isEqualTo(Integer.parseInt(hm[1]));
            assertThat(out.results().irrigationRecommended()).isEqualTo(recommended[i]);
            assertThat(out.results().idealIrrigationMm())
                    .isEqualByComparingTo(TestData.SCENARIO_A_IDEAL_IRRIGATION.get(i));
        }

        assertThat(h.balances.count()).isEqualTo(7);
        assertThat(h.balances.countCurrent()).isEqualTo(7);
        // pontos verificados do cenário
        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo("28"); // novo ciclo, não 36
        assertThat(h.current(1).result().idealIrrigation()).isEqualByComparingTo("4"); // I > 0, não recomendado
        assertThat(h.current(3).result().idealIrrigation()).isEqualByComparingTo("-1");
        assertThat(h.current(7).result().operatingTimeHours()).isEqualByComparingTo("2");
        assertThat(h.current(2).result().operatingTimeHours()).isEqualByComparingTo("0.8333333333");
    }

    // ------------------------------------------------------------ Cenário B

    @Test
    @DisplayName("Cenário B: F = 0,90, AFDf maior que CAD é mantida e vira a AFDi do dia seguinte")
    void scenarioBKeepsAfdFinalAboveCad() {
        h = new WaterBalanceServiceHarness();
        h.registerBase();
        h.replacePlanWithFactor("0.90");
        h.setWeather(1, "5.00", "20");
        h.setWeather(2, "5.00", "0");
        h.irrigation.upsert(TestData.appliedIrrigation(h.areaId, scenarioDate(1), "0"));
        h.irrigation.upsert(TestData.appliedIrrigation(h.areaId, scenarioDate(2), "0"));

        WaterBalanceOutput day1 = h.calculateDay(1);
        WaterBalanceOutput day2 = h.calculateDay(2);

        DailyWaterBalance d1 = h.current(1);
        assertThat(d1.result().cad()).isEqualByComparingTo("72");
        assertThat(d1.result().afdInitial()).isEqualByComparingTo("64.8");
        assertThat(d1.result().afdFinal()).isEqualByComparingTo("80.8");
        assertThat(d1.result().afdFinal()).isGreaterThan(d1.result().cad());
        assertThat(d1.result().idealIrrigation()).isEqualByComparingTo("-16");
        assertThat(d1.result().recommended()).isFalse();
        assertThat(day1.results().afdFinalMm()).isEqualByComparingTo("80.80");

        DailyWaterBalance d2 = h.current(2);
        assertThat(d2.result().afdInitial()).isEqualByComparingTo("80.8");
        assertThat(d2.result().afdFinal()).isEqualByComparingTo("76.8");
        assertThat(d2.result().idealIrrigation()).isEqualByComparingTo("4");
        assertThat(d2.result().recommended()).isTrue();
        assertThat(d2.result().operatingTimeHours()).isEqualByComparingTo("0.6666666667");
        assertThat(day2.results().operatingTime().hours()).isEqualByComparingTo("0.6667");
        assertThat(day2.results().operatingTime().wholeHours()).isZero();
        assertThat(day2.results().operatingTime().minutes()).isEqualTo(40);
    }
}

