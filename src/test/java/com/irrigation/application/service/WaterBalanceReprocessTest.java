package com.irrigation.application.service;

import com.irrigation.application.dto.ReprocessWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository.InjectedFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static com.irrigation.support.TestData.scenarioDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Testes unitários de {@link WaterBalanceService#reprocess} (Req 13, 12.6, 12.7), com o Cenário E de
 * requirements.md. O reprocessamento do harness roda numa transação simulada que desfaz tudo ao falhar.
 */
class WaterBalanceReprocessTest {

    private WaterBalanceServiceHarness h;

    @BeforeEach
    void setUp() {
        h = WaterBalanceServiceHarness.scenarioA(7);
    }

    private List<DailyWaterBalance> snapshot() {
        return h.balances.snapshot();
    }

    // ------------------------------------------------------------ Cenário E

    @Test
    @DisplayName("Cenário E: ETP do dia 2 corrigida e reprocesso a partir do dia 2 gera revisões 2 e preserva as antigas")
    void scenarioEReprocessesFromDayTwo() {
        h.calculateDays(7);
        List<DailyWaterBalance> originals = snapshot();
        h.setWeather(2, "7.50", "0");

        List<WaterBalanceOutput> out = h.reprocessFrom(2);

        // novas revisões, dias 2 a 7, em ordem cronológica
        assertThat(out).extracting(WaterBalanceOutput::date)
                .containsExactly(scenarioDate(2), scenarioDate(3), scenarioDate(4),
                        scenarioDate(5), scenarioDate(6), scenarioDate(7));
        assertThat(out).allSatisfy(o -> {
            assertThat(o.control().revision()).isEqualTo(2);
            assertThat(o.control().current()).isTrue();
        });

        String[] afdInitial = {"32", "26", "27", "22", "26", "22"};
        String[] afdFinal = {"26", "27", "22", "26", "22", "10"};
        String[] ideal = {"6", "-1", "5", "-4", "4", "12"};
        for (int i = 0; i < 6; i++) {
            DailyWaterBalance stored = h.current(i + 2);
            assertThat(stored.revision()).isEqualTo(2);
            assertThat(stored.result().afdInitial()).isEqualByComparingTo(afdInitial[i]);
            assertThat(stored.result().afdFinal()).isEqualByComparingTo(afdFinal[i]);
            assertThat(stored.result().idealIrrigation()).isEqualByComparingTo(ideal[i]);
            assertThat(out.get(i).id()).isEqualTo(stored.id());
        }
        // dia 2: ETc = 6, tempo = 1,0000 h
        assertThat(h.current(2).result().etc()).isEqualByComparingTo("6");
        assertThat(h.current(2).result().recommended()).isTrue();
        assertThat(h.current(2).result().operatingTimeHours()).isEqualByComparingTo("1");
        assertThat(out.get(0).results().operatingTime().hours()).isEqualByComparingTo("1.0000");
        assertThat(out.get(0).results().operatingTime().minutes()).isZero();
        assertThat(out.get(0).results().operatingTime().wholeHours()).isEqualTo(1);
        // dia 3: sem recomendação por I <= 0
        assertThat(h.current(3).result().recommended()).isFalse();
        assertThat(h.current(3).result().operatingTimeHours()).isEqualByComparingTo("0");

        // dia 1 não foi tocado: continua revisão 1 corrente
        assertThat(h.current(1)).isEqualTo(originals.get(0));
        assertThat(h.balances.findRevisions(h.areaId, scenarioDate(1))).hasSize(1);

        // revisões antigas preservadas, sem alteração de valores, e não correntes
        for (int day = 2; day <= 7; day++) {
            List<DailyWaterBalance> revisions = h.balances.findRevisions(h.areaId, scenarioDate(day));
            assertThat(revisions).extracting(DailyWaterBalance::revision).containsExactly(2, 1);
            assertThat(revisions).extracting(DailyWaterBalance::current).containsExactly(true, false);
            assertThat(revisions.get(1)).isEqualTo(originals.get(day - 1).markNotCurrent());
            assertThat(revisions.get(1).result()).isEqualTo(originals.get(day - 1).result());
            assertThat(revisions.get(1).inputs()).isEqualTo(originals.get(day - 1).inputs());
        }
        assertThat(h.balances.count()).isEqualTo(13);
        assertThat(h.balances.countCurrent()).isEqualTo(7);
    }

    @Test
    @DisplayName("DP-06: reprocessar atravessando o início do segundo plano continua a cadeia, sem reiniciar")
    void reprocessAcrossSecondPlanContinuesTheChain() {
        h.calculateDays(3);
        h.startSecondPlanAt(4);
        h.calculateDay(4);
        h.calculateDay(5);
        List<DailyWaterBalance> originals = snapshot();
        h.setWeather(2, "7.50", "0");

        h.reprocessFrom(1);

        for (int day = 1; day <= 5; day++) {
            assertThat(h.current(day).revision()).isEqualTo(2);
            assertThat(h.current(day).inputs().firstDayOfPlan()).isEqualTo(day == 1);
        }
        assertThat(h.current(4).managementPlanId()).isNotEqualTo(h.current(3).managementPlanId());
        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo(h.current(3).result().afdFinal());
        assertThat(h.current(5).result().afdInitial()).isEqualByComparingTo(h.current(4).result().afdFinal());
        assertThat(h.current(4).result().noRecommendationReason()).isNull();
        // a correção do dia 2 propaga ao segundo plano (4 não reinicia em CAD × F = 36)
        assertThat(h.current(4).result().afdInitial())
                .isNotEqualByComparingTo(originals.get(3).result().afdInitial());
    }

    @Test
    @DisplayName("DP-06: reprocessar a partir do início do segundo plano obtém a AFDi da corrente do dia anterior")
    void reprocessFromSecondPlanStartUsesPreviousDayAfdFinal() {
        h.calculateDays(3);
        h.startSecondPlanAt(4);
        h.calculateDay(4);
        h.calculateDay(5);

        h.reprocessFrom(4);

        assertThat(h.current(4).revision()).isEqualTo(2);
        assertThat(h.current(4).inputs().firstDayOfPlan()).isFalse();
        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo(h.current(3).result().afdFinal());
        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo("28");
        assertThat(h.current(3).revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Req 13.1: dias nunca calculados não são calculados pelo reprocessamento")
    void neverCalculatedDaysAreNotCalculated() {
        h.calculateDays(4);

        List<WaterBalanceOutput> out = h.reprocessFrom(2);

        assertThat(out).extracting(WaterBalanceOutput::date)
                .containsExactly(scenarioDate(2), scenarioDate(3), scenarioDate(4));
        assertThat(h.balances.findCurrent(h.areaId, scenarioDate(5))).isEmpty();
        assertThat(h.balances.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("Req 13.2: reprocessar sem mudança de dados cria a revisão seguinte com os mesmos valores e o mesmo TR")
    void reprocessWithoutChangesKeepsValuesAndCycleDays() {
        h.calculateDays(3);
        List<DailyWaterBalance> before = snapshot();

        h.reprocessFrom(1);
        h.reprocessFrom(1);

        for (int day = 1; day <= 3; day++) {
            DailyWaterBalance now = h.current(day);
            DailyWaterBalance original = before.get(day - 1);
            assertThat(now.revision()).isEqualTo(3);
            assertThat(now.cycleDay()).isEqualTo(original.cycleDay());
            assertThat(now.result().afdInitial()).isEqualByComparingTo(original.result().afdInitial());
            assertThat(now.result().afdFinal()).isEqualByComparingTo(original.result().afdFinal());
            assertThat(h.balances.findRevisions(h.areaId, scenarioDate(day)))
                    .extracting(DailyWaterBalance::revision).containsExactly(3, 2, 1);
            assertThat(h.balances.findRevisions(h.areaId, scenarioDate(day)).stream()
                    .filter(DailyWaterBalance::current).count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Req 13.3: reprocessar a partir do dia 1 reaplica CAD × F com o F vigente; a revisão antiga mantém o F antigo")
    void reprocessFromPlanStartUsesCurrentFactor() {
        h.calculateDays(2);
        h.replacePlanWithFactor("0.90");

        h.reprocessFrom(1);

        assertThat(h.current(1).inputs().availabilityFactor().value()).isEqualByComparingTo("0.90");
        assertThat(h.current(1).result().afdInitial()).isEqualByComparingTo("64.8");
        assertThat(h.current(2).result().afdInitial()).isEqualByComparingTo(h.current(1).result().afdFinal());
        DailyWaterBalance old = h.balances.findRevisions(h.areaId, scenarioDate(1)).get(1);
        assertThat(old.current()).isFalse();
        assertThat(old.inputs().availabilityFactor().value()).isEqualByComparingTo("0.50");
        assertThat(old.result().afdInitial()).isEqualByComparingTo("36");
    }

    @Test
    @DisplayName("Req 13.3: reprocessar a partir de data posterior usa a AFDf corrente do dia anterior")
    void reprocessFromLaterDateStartsFromPreviousCurrentAfdFinal() {
        h.calculateDays(4);
        h.setWeather(4, "10.00", "0");

        h.reprocessFrom(4);

        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo(h.current(3).result().afdFinal());
        assertThat(h.current(4).result().afdInitial()).isEqualByComparingTo("28");
        assertThat(h.current(4).result().afdFinal()).isEqualByComparingTo("20");
        assertThat(h.current(4).revision()).isEqualTo(2);
        assertThat(h.current(3).revision()).isEqualTo(1);
    }

    // ------------------------------------------------------------ Data não calculada

    @Test
    @DisplayName("Req 13.7: data inicial sem revisão corrente é 422 e nada é criado")
    void startDateNotCalculatedIsUnprocessable() {
        h.calculateDays(3);
        List<DailyWaterBalance> before = snapshot();

        for (int day : new int[]{4, 7}) {
            assertThatThrownBy(() -> h.reprocessFrom(day))
                    .isInstanceOf(BusinessRuleException.class)
                    .isNotInstanceOf(PreconditionsNotMetException.class)
                    .hasMessageContaining(scenarioDate(day).toString());
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Req 13.7: área sem nenhum balanço calculado é 422")
    void areaWithoutBalancesIsUnprocessable() {
        assertThatThrownBy(() -> h.reprocessFrom(1)).isInstanceOf(BusinessRuleException.class);
        assertThat(h.balances.count()).isZero();
    }

    // ------------------------------------------------------------ Pré-condições (Req 13.4)

    @Test
    @DisplayName("Req 13.4: pré-condição violada num dia posterior aborta com a data violadora e não grava nada")
    void preconditionViolatedOnLaterDayAbortsWithoutWriting() {
        h.calculateDays(7);
        List<DailyWaterBalance> before = snapshot();
        h.setWeather(2, "7.50", "0"); // mudança válida que seria gravada nos dias 2 a 4
        h.weather.hide(scenarioDate(5));

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(2));

        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        PreconditionsNotMetException e = (PreconditionsNotMetException) thrown;
        assertThat(e.getDate()).isEqualTo(scenarioDate(5));
        assertThat(e.getPreconditions()).extracting(p -> p.code()).containsExactly(Code.WEATHER_MISSING);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(h.balances.countCurrent()).isEqualTo(7);
    }

    @Test
    @DisplayName("Req 13.4: Irrigação Real ausente no último dia também aborta tudo")
    void missingAppliedIrrigationOnLastDayAbortsEverything() {
        h.calculateDays(7);
        List<DailyWaterBalance> before = snapshot();
        h.irrigation.hide(scenarioDate(7));

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(1));

        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        assertThat(((PreconditionsNotMetException) thrown).getDate()).isEqualTo(scenarioDate(7));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Req 13.4 e 11.5: solo e cultura incompletos no primeiro dia reprocessado são agregados e nada é gravado")
    void aggregatedPreconditionsOnFirstReprocessedDayAbort() {
        h.calculateDays(3);
        List<DailyWaterBalance> before = snapshot();
        h.replaceSoil(null, null, "1.2");
        h.replaceCrop(null, "400");

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(2));

        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        PreconditionsNotMetException e = (PreconditionsNotMetException) thrown;
        assertThat(e.getDate()).isEqualTo(scenarioDate(2));
        assertThat(e.getPreconditions()).extracting(p -> p.code())
                .containsExactly(Code.SOIL_FIELDS_MISSING, Code.CROP_FIELDS_MISSING);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Req 13.4 e 13.5: falha de gravação no meio das novas revisões desfaz a operação inteira")
    void persistenceFailureRollsBackTheWholeReprocess() {
        h.calculateDays(4);
        List<DailyWaterBalance> before = snapshot();
        h.balances.failOnInsertNumber(2);

        assertThatThrownBy(() -> h.reprocessFrom(1)).isInstanceOf(InjectedFailureException.class);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(h.balances.countCurrent()).isEqualTo(4);
    }

    @Test
    @DisplayName("Req 13.4 e 13.5: falha ao marcar as revisões antigas também desfaz tudo")
    void markNotCurrentFailureRollsBack() {
        h.calculateDays(3);
        List<DailyWaterBalance> before = snapshot();
        h.balances.failOnMarkNotCurrent();

        assertThatThrownBy(() -> h.reprocessFrom(1)).isInstanceOf(InjectedFailureException.class);

        assertThat(snapshot()).isEqualTo(before);
    }

    // ------------------------------------------------------------ Ordem 400, 404, 409, 422

    @Test
    @DisplayName("Req 13: formato inválido é 400 antes de qualquer consulta")
    void invalidFormatIsBadRequest() {
        h.lock.setAvailable(false);

        assertThatThrownBy(() -> h.service.reprocess(UUID.randomUUID(), new ReprocessWaterBalanceInput(null)))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> h.service.reprocess(h.areaId, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(h.lock.callCount()).isZero();
    }

    @Test
    @DisplayName("Req 13: área inexistente é 404, antes do bloqueio")
    void unknownAreaIsNotFound() {
        h.lock.setAvailable(false);

        assertThatThrownBy(() -> h.service.reprocess(UUID.randomUUID(),
                new ReprocessWaterBalanceInput(scenarioDate(1))))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(h.lock.callCount()).isZero();
    }

    @Test
    @DisplayName("Req 13.8: bloqueio indisponível é 409 antes da verificação da data, sem criar revisão")
    void unavailableLockIsConflict() {
        h.calculateDays(3);
        List<DailyWaterBalance> before = snapshot();
        int callsBefore = h.lock.callCount();
        h.lock.setAvailable(false);

        // data 7 nunca calculada: só chegaria a 422 se o bloqueio fosse obtido
        assertThatThrownBy(() -> h.reprocessFrom(7)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> h.reprocessFrom(1)).isInstanceOf(ConflictException.class);

        assertThat(h.lock.callCount()).isEqualTo(callsBefore + 2);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Req 12.6: dados do plano alterados depois do cálculo só afetam revisões novas")
    void changesAfterCalculationOnlyAffectNewRevisions() {
        h.calculateDays(2);
        DailyWaterBalance day1 = h.current(1);
        h.replaceCrop("1.0", "400");
        h.replaceSoil("30", "15", "1.2");

        assertThat(h.current(1)).isEqualTo(day1);
        h.reprocessFrom(2);
        assertThat(h.current(1)).isEqualTo(day1);
        assertThat(h.current(2).inputs().kc().value()).isEqualByComparingTo(bd("1.0"));
        assertThat(h.balances.findRevisions(h.areaId, scenarioDate(2)).get(1).inputs().kc().value())
                .isEqualByComparingTo("0.8");
    }
}

