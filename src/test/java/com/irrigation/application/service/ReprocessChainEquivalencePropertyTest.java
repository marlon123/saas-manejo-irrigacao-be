package com.irrigation.application.service;

import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.waterbalance.WaterBalanceCalculator;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.irrigation.support.TestData.scenarioDate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 20: o reprocessamento equivale à cadeia recalculada com o {@link WaterBalanceCalculator}.
 *
 * <p>Para séries arbitrárias já calculadas (Cenário A com ETP, P e IR gerados) e uma data inicial
 * arbitrária, depois de alterar ETP, P, IR e F, cada dia calculado a partir da data inicial deve ter uma nova
 * revisão corrente com exatamente os valores da cadeia recalculada (AFDi do primeiro dia = AFDf corrente do
 * dia anterior, ou CAD × F no primeiro dia do plano; demais = AFDf da nova revisão do dia anterior). As
 * revisões antigas ficam intactas e não correntes, os dias anteriores à data inicial não mudam e os dias
 * nunca calculados continuam sem balanço.
 *
 * <p><b>Validates: Requirements 13.1, 13.2, 13.3, 12.7</b>
 */
class ReprocessChainEquivalencePropertyTest {

    private static final int DAYS = 7;
    private static final WaterBalanceCalculator CALCULATOR = new WaterBalanceCalculator();

    private static String mm(int hundredths) {
        return BigDecimal.valueOf(hundredths, 2).toPlainString();
    }

    // Feature: irrigation-water-balance-module, Property 20: Reprocessamento equivale à cadeia recalculada
    @Property(tries = 150)
    void reprocessEqualsRecalculatedChain(
            @ForAll @Size(DAYS) List<@IntRange(min = 1, max = 2000) Integer> etp,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> precipitation,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> irrigation,
            @ForAll @Size(DAYS) List<@IntRange(min = 1, max = 2000) Integer> newEtp,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> newPrecipitation,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> newIrrigation,
            @ForAll @Size(DAYS) List<Boolean> changeDay,
            @ForAll boolean changeFactor,
            @ForAll @IntRange(min = 10, max = 100) int newFactorPercent,
            @ForAll @IntRange(min = 1, max = DAYS) int calculatedDays,
            @ForAll @IntRange(min = 0, max = 1000) int startSeed) {

        WaterBalanceServiceHarness h = seededHarness(etp, precipitation, irrigation);
        h.calculateDays(calculatedDays);
        int start = 1 + startSeed % calculatedDays;
        List<DailyWaterBalance> before = h.balances.snapshot();

        for (int d = 1; d <= DAYS; d++) {
            if (changeDay.get(d - 1)) {
                h.weather.upsert(com.irrigation.support.TestData.dailyWeather(h.areaId, scenarioDate(d),
                        mm(newEtp.get(d - 1)), mm(newPrecipitation.get(d - 1))));
                h.irrigation.upsert(com.irrigation.support.TestData.appliedIrrigation(h.areaId, scenarioDate(d),
                        mm(newIrrigation.get(d - 1))));
            }
        }
        if (changeFactor) {
            h.replacePlanWithFactor(BigDecimal.valueOf(newFactorPercent, 2).toPlainString());
        }

        // cadeia esperada, calculada do zero com os dados atuais
        List<WaterBalanceResult> expected = new ArrayList<>();
        BigDecimal previousAfdFinal = start == 1 ? null : h.current(start - 1).result().afdFinal();
        for (int d = start; d <= calculatedDays; d++) {
            WaterBalanceInputs in = new WaterBalanceInputs(
                    new EtpDepth(h.weather.findByAreaAndDate(h.areaId, scenarioDate(d)).orElseThrow()
                            .getEtp().value()),
                    new PrecipitationDepth(h.weather.findByAreaAndDate(h.areaId, scenarioDate(d)).orElseThrow()
                            .getPrecipitation().value()),
                    new AppliedIrrigationDepth(h.irrigation.findByAreaAndDate(h.areaId, scenarioDate(d))
                            .orElseThrow().getDepth().value()),
                    h.soil.getFieldCapacityGravimetric(), h.soil.getPermanentWiltingPointGravimetric(),
                    h.soil.getBulkDensity(), h.crop.getRootDepth(), h.crop.getKc(),
                    new AvailabilityFactor(h.plan.getAvailabilityFactor().value()), h.plan.getSprinklerRate(),
                    previousAfdFinal == null, previousAfdFinal);
            WaterBalanceResult r = CALCULATOR.calculate(in);
            expected.add(r);
            previousAfdFinal = r.afdFinal();
        }

        h.reprocessFrom(start);

        // novas revisões iguais à cadeia recalculada
        for (int d = start; d <= calculatedDays; d++) {
            DailyWaterBalance now = h.current(d);
            DailyWaterBalance old = before.get(d - 1);
            assertResultEquals(now.result(), expected.get(d - start));
            assertThat(now.revision()).isEqualTo(old.revision() + 1);
            assertThat(now.current()).isTrue();
            assertThat(now.cycleDay()).isEqualTo(old.cycleDay());
            assertThat(now.id()).isNotEqualTo(old.id());
            List<DailyWaterBalance> revisions = h.balances.findRevisions(h.areaId, scenarioDate(d));
            assertThat(revisions).extracting(DailyWaterBalance::revision).containsExactly(2, 1);
            assertThat(revisions.stream().filter(DailyWaterBalance::current).count()).isEqualTo(1);
            // revisão antiga: valores intactos, não corrente
            assertThat(revisions.get(1)).isEqualTo(old.markNotCurrent());
        }
        // dias anteriores à data inicial: intactos
        for (int d = 1; d < start; d++) {
            assertThat(h.current(d)).isEqualTo(before.get(d - 1));
            assertThat(h.balances.findRevisions(h.areaId, scenarioDate(d))).hasSize(1);
        }
        // dias nunca calculados continuam sem balanço
        for (int d = calculatedDays + 1; d <= DAYS; d++) {
            assertThat(h.balances.findCurrent(h.areaId, scenarioDate(d))).isEmpty();
            assertThat(h.balances.findRevisions(h.areaId, scenarioDate(d))).isEmpty();
        }
        assertThat(h.balances.count()).isEqualTo(before.size() + (calculatedDays - start + 1));
        assertThat(h.balances.countCurrent()).isEqualTo(calculatedDays);
    }

    // Feature: irrigation-water-balance-module, Property 20: Reprocessamento equivale à cadeia recalculada
    @Property(tries = 100)
    void reprocessWithoutChangesReproducesSameValues(
            @ForAll @Size(DAYS) List<@IntRange(min = 1, max = 2000) Integer> etp,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> precipitation,
            @ForAll @Size(DAYS) List<@IntRange(min = 0, max = 3000) Integer> irrigation,
            @ForAll @IntRange(min = 1, max = DAYS) int calculatedDays,
            @ForAll @IntRange(min = 0, max = 1000) int startSeed) {

        WaterBalanceServiceHarness h = seededHarness(etp, precipitation, irrigation);
        h.calculateDays(calculatedDays);
        int start = 1 + startSeed % calculatedDays;
        List<DailyWaterBalance> before = h.balances.snapshot();

        h.reprocessFrom(start);

        for (int d = start; d <= calculatedDays; d++) {
            DailyWaterBalance now = h.current(d);
            DailyWaterBalance old = before.get(d - 1);
            assertResultEquals(now.result(), old.result());
            assertThat(now.revision()).isEqualTo(old.revision() + 1);
            assertThat(now.cycleDay()).isEqualTo(old.cycleDay());
            assertThat(h.balances.findRevisions(h.areaId, scenarioDate(d)).get(1)).isEqualTo(old.markNotCurrent());
        }
        for (int d = 1; d < start; d++) {
            assertThat(h.current(d)).isEqualTo(before.get(d - 1));
        }
        assertThat(h.balances.countCurrent()).isEqualTo(calculatedDays);
    }

    /** Cenário A (área, solo, cultura e plano) com meteorologia e IR gerados para os 7 dias. */
    private static WaterBalanceServiceHarness seededHarness(List<Integer> etp, List<Integer> precipitation,
                                                            List<Integer> irrigation) {
        WaterBalanceServiceHarness h = new WaterBalanceServiceHarness();
        h.registerBase();
        for (int d = 1; d <= DAYS; d++) {
            h.weather.upsert(com.irrigation.support.TestData.dailyWeather(h.areaId, scenarioDate(d),
                    mm(etp.get(d - 1)), mm(precipitation.get(d - 1))));
            h.irrigation.upsert(com.irrigation.support.TestData.appliedIrrigation(h.areaId, scenarioDate(d),
                    mm(irrigation.get(d - 1))));
        }
        return h;
    }

    private static void assertResultEquals(WaterBalanceResult actual, WaterBalanceResult expected) {
        assertThat(actual.etc()).isEqualByComparingTo(expected.etc());
        assertThat(actual.cad()).isEqualByComparingTo(expected.cad());
        assertThat(actual.afdInitial()).isEqualByComparingTo(expected.afdInitial());
        assertThat(actual.afdFinal()).isEqualByComparingTo(expected.afdFinal());
        assertThat(actual.idealIrrigation()).isEqualByComparingTo(expected.idealIrrigation());
        assertThat(actual.recommended()).isEqualTo(expected.recommended());
        assertThat(actual.noRecommendationReason()).isEqualTo(expected.noRecommendationReason());
        assertThat(actual.operatingTimeHours()).isEqualByComparingTo(expected.operatingTimeHours());
    }
}
