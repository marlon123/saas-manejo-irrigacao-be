package com.irrigation.domain.waterbalance;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 9: determinismo do calculador.
 *
 * <p>Para qualquer entrada válida, execuções repetidas, em instâncias distintas do calculador e
 * em threads diferentes, produzem resultados idênticos, com os mesmos valores e as mesmas escalas
 * (a igualdade do record usa {@code BigDecimal.equals}, que considera a escala).
 *
 * <p><b>Validates: Requirements 15.9</b>
 */
class CalculatorDeterminismPropertyTest {

    @Provide
    Arbitrary<WaterBalanceInputs> anyDayInputs() {
        return Arbitraries.of(true, false).flatMap(WaterBalanceArbitraries::inputs);
    }

    // Feature: irrigation-water-balance-module, Property 9: Determinismo do calculador
    @Property(tries = 300)
    void repeatedCalculationsOnSameAndDistinctInstancesAreIdentical(
            @ForAll("anyDayInputs") WaterBalanceInputs in) {
        WaterBalanceCalculator shared = new WaterBalanceCalculator();
        WaterBalanceResult first = shared.calculate(in);

        for (int i = 0; i < 5; i++) {
            assertThat(shared.calculate(in)).isEqualTo(first);
            assertThat(new WaterBalanceCalculator().calculate(in)).isEqualTo(first);
        }
        assertSameScales(first, new WaterBalanceCalculator().calculate(in));
    }

    // Feature: irrigation-water-balance-module, Property 9: Determinismo do calculador
    @Property(tries = 100)
    void concurrentCalculationsFromDifferentThreadsAreIdentical(
            @ForAll("anyDayInputs") WaterBalanceInputs in) throws Exception {
        WaterBalanceResult expected = new WaterBalanceCalculator().calculate(in);
        WaterBalanceCalculator shared = new WaterBalanceCalculator();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<WaterBalanceResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Callable<WaterBalanceResult> task = i % 2 == 0
                        ? () -> shared.calculate(in)
                        : () -> new WaterBalanceCalculator().calculate(in);
                futures.add(pool.submit(task));
            }
            for (Future<WaterBalanceResult> f : futures) {
                WaterBalanceResult actual = f.get();
                assertThat(actual).isEqualTo(expected);
                assertSameScales(expected, actual);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertSameScales(WaterBalanceResult a, WaterBalanceResult b) {
        assertThat(b.etc().scale()).isEqualTo(a.etc().scale());
        assertThat(b.afdInitial().scale()).isEqualTo(a.afdInitial().scale());
        assertThat(b.afdFinal().scale()).isEqualTo(a.afdFinal().scale());
        assertThat(b.idealIrrigation().scale()).isEqualTo(a.idealIrrigation().scale());
    }
}
