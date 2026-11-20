package com.irrigation.application.service;

import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.model.DailyWaterBalance;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.irrigation.support.TestData.scenarioDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Propriedade 18: um segundo cálculo para uma data já calculada, sem reprocessamento, é recusado com 422 e deixa o
 * registro corrente inalterado. Em sequências arbitrárias de cálculos e reprocessamentos existe exatamente um
 * balanço corrente por data calculada, e as revisões anteriores seguem não correntes com os valores intactos.
 *
 * <p>Usa o Cenário A (7 dias de meteorologia e irrigação) do {@link WaterBalanceServiceHarness}, sem alterá-lo.
 *
 * <p>**Validates: Requirements 11.7, 12.9, 13.2**
 */
class RepeatedCalculationPropertyTest {

    private static final int DAYS = 7;

    /** Operação sobre o dia {@code day} (1 a 7) do Cenário A. */
    private record Op(boolean reprocess, int day) {
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        Arbitrary<Op> op = Arbitraries.integers().between(1, DAYS)
                .flatMap(day -> Arbitraries.of(true, false).map(r -> new Op(r, day)));
        return op.list().ofMinSize(1).ofMaxSize(25);
    }

    // Feature: irrigation-water-balance-module, Property 18: Cálculo repetido não duplica o corrente
    @Property(tries = 100)
    void repeatedCalculationNeverDuplicatesCurrent(@ForAll("operations") List<Op> ops) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(DAYS);
        Set<Integer> calculated = new HashSet<>();
        Map<UUID, DailyWaterBalance> seen = new HashMap<>();

        for (Op op : ops) {
            List<DailyWaterBalance> before = h.balances.snapshot();
            if (op.reprocess()) {
                applyReprocess(h, op.day(), calculated, before);
            } else {
                applyCalculate(h, op.day(), calculated, before);
            }
            assertInvariants(h, calculated, seen);
        }
    }

    private void applyCalculate(WaterBalanceServiceHarness h, int day, Set<Integer> calculated,
                                List<DailyWaterBalance> before) {
        boolean already = calculated.contains(day);
        boolean gap = day > 1 && !calculated.contains(day - 1);
        Throwable thrown = catchThrowable(() -> h.calculateDay(day));
        if (already) {
            assertThat(thrown).isInstanceOf(BusinessRuleException.class)
                    .isNotInstanceOf(PreconditionsNotMetException.class)
                    .hasMessageContaining("já foi calculada")
                    .hasMessageContaining(scenarioDate(day).toString());
            assertThat(h.balances.snapshot()).isEqualTo(before);
        } else if (gap) {
            assertThat(thrown).isInstanceOf(BusinessRuleException.class);
            assertThat(h.balances.snapshot()).isEqualTo(before);
        } else {
            assertThat(thrown).isNull();
            calculated.add(day);
            assertThat(h.current(day).revision()).isEqualTo(1);
        }
    }

    private void applyReprocess(WaterBalanceServiceHarness h, int day, Set<Integer> calculated,
                                List<DailyWaterBalance> before) {
        Throwable thrown = catchThrowable(() -> h.reprocessFrom(day));
        if (!calculated.contains(day)) {
            assertThat(thrown).isInstanceOf(BusinessRuleException.class);
            assertThat(h.balances.snapshot()).isEqualTo(before);
        } else {
            assertThat(thrown).isNull();
        }
    }

    private void assertInvariants(WaterBalanceServiceHarness h, Set<Integer> calculated,
                                  Map<UUID, DailyWaterBalance> seen) {
        List<DailyWaterBalance> all = h.balances.snapshot();
        Map<LocalDate, List<DailyWaterBalance>> byDate = all.stream()
                .collect(Collectors.groupingBy(DailyWaterBalance::date));

        // datas com registros = datas calculadas; exatamente um corrente por data
        assertThat(byDate.keySet()).containsExactlyInAnyOrderElementsOf(
                calculated.stream().map(d -> scenarioDate(d)).toList());
        assertThat(h.balances.countCurrent()).isEqualTo(calculated.size());
        byDate.forEach((date, rows) -> {
            assertThat(rows.stream().filter(DailyWaterBalance::current).count()).isEqualTo(1);
            assertThat(rows).extracting(DailyWaterBalance::revision)
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.IntStream.rangeClosed(1, rows.size()).boxed().toList());
            DailyWaterBalance current = h.balances.findCurrent(h.areaId, date).orElseThrow();
            assertThat(current.revision()).isEqualTo(rows.size());
        });

        // revisões já vistas: o id existe, e as não correntes mantêm todos os valores
        for (DailyWaterBalance row : all) {
            DailyWaterBalance previous = seen.get(row.id());
            if (previous != null) {
                DailyWaterBalance expected = row.current() ? previous : previous.markNotCurrent();
                if (row.current()) {
                    assertThat(row).isEqualTo(previous);
                } else {
                    assertThat(row).isEqualTo(expected);
                }
            }
            seen.put(row.id(), row);
        }
        assertThat(all.stream().map(DailyWaterBalance::id).collect(Collectors.toSet())).containsAll(seen.keySet());
    }
}
