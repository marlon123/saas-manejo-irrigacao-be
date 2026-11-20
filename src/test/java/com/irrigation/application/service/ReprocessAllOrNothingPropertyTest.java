package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository.InjectedFailureException;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Propriedade 21: reprocessamento tudo-ou-nada (Req 13.4, 13.5, 7.2, 12.5; DD-02).
 *
 * <p>O rollback é o simulado por {@code InMemoryDailyWaterBalanceRepository#atomically} (usado por
 * {@code WaterBalanceServiceHarness#reprocessFrom}); o {@code @Transactional} real do serviço é verificado no
 * teste de integração. Em {@code calculate} há um único insert, sem transação simulada: a ausência de balanço
 * parcial decorre de o repositório em memória desfazer a própria chamada que falha.
 */
class ReprocessAllOrNothingPropertyTest {

    private static final int MAX_DAYS = 7;

    /** Cadeia calculada de 1 a {@code days} dias e o início do reprocessamento (1 a days). */
    record Chain(int days, int start) {
    }

    @Provide
    Arbitrary<Chain> chains() {
        return Arbitraries.integers().between(1, MAX_DAYS).flatMap(days ->
                Arbitraries.integers().between(1, days).map(start -> new Chain(days, start)));
    }

    // Feature: irrigation-water-balance-module, Property 21: Reprocessamento tudo-ou-nada
    @Property(tries = 100)
    void missingWeatherOrIrrigationOnAnyDayAbortsAndKeepsState(
            @ForAll("chains") Chain chain, @ForAll boolean hideWeather, @ForAll("offsets") int offset) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(chain.days());
        h.calculateDays(chain.days());
        List<DailyWaterBalance> before = h.balances.snapshot();
        int violatingDay = chain.start() + offset % (chain.days() - chain.start() + 1);
        if (hideWeather) {
            h.weather.hide(TestData.scenarioDate(violatingDay));
        } else {
            h.irrigation.hide(TestData.scenarioDate(violatingDay));
        }
        h.setWeather(chain.start(), "7.50", "0"); // mudança válida que seria gravada se não houvesse o abort

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(chain.start()));

        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        PreconditionsNotMetException e = (PreconditionsNotMetException) thrown;
        assertThat(e.getDate()).isEqualTo(TestData.scenarioDate(violatingDay));
        assertThat(h.balances.snapshot()).isEqualTo(before);
        assertThat(h.balances.countCurrent()).isEqualTo(chain.days());
    }

    // Feature: irrigation-water-balance-module, Property 21: Reprocessamento tudo-ou-nada
    @Property(tries = 100)
    void missingSoilOrCropFieldsAbortAndKeepState(
            @ForAll("chains") Chain chain, @ForAll("soilCropBreakage") int[] breakage) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(chain.days());
        h.calculateDays(chain.days());
        List<DailyWaterBalance> before = h.balances.snapshot();
        boolean breakSoil = breakage[0] == 1 || breakage[0] == 3;
        boolean breakCrop = breakage[0] == 2 || breakage[0] == 3;
        if (breakSoil) {
            h.replaceSoil(null, null, "1.2");
        }
        if (breakCrop) {
            h.replaceCrop(null, "400");
        }

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(chain.start()));

        assertThat(thrown).isInstanceOf(PreconditionsNotMetException.class);
        assertThat(((PreconditionsNotMetException) thrown).getDate())
                .isEqualTo(TestData.scenarioDate(chain.start()));
        assertThat(h.balances.snapshot()).isEqualTo(before);
    }

    // Feature: irrigation-water-balance-module, Property 21: Reprocessamento tudo-ou-nada
    @Property(tries = 100)
    void injectedFailureAtAnyWritePointLeavesStateUntouched(
            @ForAll("chains") Chain chain, @ForAll boolean failOnMark, @ForAll("offsets") int offset) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(chain.days());
        h.calculateDays(chain.days());
        List<DailyWaterBalance> before = h.balances.snapshot();
        int reprocessed = chain.days() - chain.start() + 1;
        h.setWeather(chain.start(), "7.50", "0");
        if (failOnMark) {
            h.balances.failOnMarkNotCurrent();
        } else {
            h.balances.failOnInsertNumber(1 + offset % reprocessed);
        }

        Throwable thrown = catchThrowable(() -> h.reprocessFrom(chain.start()));

        assertThat(thrown).isInstanceOf(InjectedFailureException.class);
        assertThat(h.balances.snapshot()).isEqualTo(before);
        assertThat(h.balances.countCurrent()).isEqualTo(chain.days());
        assertThat(h.balances.count()).isEqualTo(chain.days());
    }

    // Feature: irrigation-water-balance-module, Property 21: Reprocessamento tudo-ou-nada
    @Property(tries = 100)
    void writeFailureOnDailyCalculationLeavesNoPartialBalance(@ForAll("chains") Chain chain) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(chain.days());
        h.calculateDays(chain.start() - 1);
        List<DailyWaterBalance> before = h.balances.snapshot();
        h.balances.failOnInsertNumber(1);

        Throwable thrown = catchThrowable(() -> h.service.calculate(h.areaId, new CalculateWaterBalanceInput(
                TestData.scenarioDate(chain.start()), TestData.SCENARIO_A_CYCLE_DAYS.get(chain.start() - 1))));

        assertThat(thrown).isInstanceOf(InjectedFailureException.class);
        assertThat(h.balances.snapshot()).isEqualTo(before);
        assertThat(h.balances.findCurrent(h.areaId, TestData.scenarioDate(chain.start()))).isEmpty();
    }

    @Provide
    Arbitrary<Integer> offsets() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<int[]> soilCropBreakage() {
        return Combinators.combine(Arbitraries.integers().between(1, 3), Arbitraries.just(0))
                .as((kind, ignored) -> new int[]{kind});
    }
}
