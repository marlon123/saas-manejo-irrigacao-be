package com.irrigation.application.service;

import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.List;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 19: imutabilidade do histórico frente a alterações de cadastro.
 *
 * <p>Depois de calcular alguns dias, alterações arbitrárias de solo, cultura, área, fator F, taxa do aspersor,
 * meteorologia e irrigação real não mudam nenhum valor (entradas, resultados, revisão, indicador de corrente)
 * dos balanços já gravados. A alteração de meteorologia ou IR só tem efeito depois de um reprocessamento, e
 * mesmo este preserva as revisões antigas como histórico não corrente.
 *
 * <p>**Validates: Requirements 4.9, 5.8, 6.9, 12.6, 12.7**
 */
class HistoryImmutabilityPropertyTest {

    /** Alteração de cadastro; {@code kind} escolhe o tipo e {@code a}, {@code b}, {@code c} são valores brutos. */
    record Change(int kind, int a, int b, int c) {
    }

    @Provide
    Arbitrary<List<Change>> changes() {
        Arbitrary<Change> change = Arbitraries.integers().between(0, 6).flatMap(kind ->
                Arbitraries.integers().between(1, 1000).flatMap(a ->
                        Arbitraries.integers().between(1, 1000).flatMap(b ->
                                Arbitraries.integers().between(1, 1000).map(c -> new Change(kind, a, b, c)))));
        return change.list().ofMinSize(1).ofMaxSize(8);
    }

    // Feature: irrigation-water-balance-module, Property 19: Imutabilidade do histórico frente a alterações de cadastro
    @Property(tries = 100)
    void registeredHistoryDoesNotChangeWhenRegistrationDataChanges(
            @ForAll("changes") List<Change> changes,
            @ForAll("daysCalculated") int days) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(7);
        h.calculateDays(days);
        List<DailyWaterBalance> before = h.balances.snapshot();
        assertThat(before).hasSize(days);

        for (Change change : changes) {
            apply(h, change, days);
        }

        // Nenhuma entrada, resultado, revisão ou indicador mudou, e nenhum registro novo surgiu
        assertThat(h.balances.snapshot()).isEqualTo(before);
        for (DailyWaterBalance b : before) {
            DailyWaterBalance read = h.balances.findCurrent(b.areaId(), b.date()).orElseThrow();
            assertThat(read).isEqualTo(b);
            assertThat(read.inputs()).isEqualTo(b.inputs());
            assertThat(read.result()).isEqualTo(b.result());
            assertThat(h.balances.findRevisions(b.areaId(), b.date())).containsExactly(b);
        }

        // Reprocessar aplica os dados alterados em nova revisão, sem tocar nas antigas
        h.reprocessFrom(1);
        for (DailyWaterBalance old : before) {
            List<DailyWaterBalance> revisions = h.balances.findRevisions(old.areaId(), old.date());
            assertThat(revisions).hasSize(2);
            DailyWaterBalance preserved = revisions.get(1);
            assertThat(preserved).isEqualTo(old.markNotCurrent());
            assertThat(preserved.inputs()).isEqualTo(old.inputs());
            assertThat(preserved.result()).isEqualTo(old.result());
        }
    }

    @Provide
    Arbitrary<Integer> daysCalculated() {
        return Arbitraries.integers().between(1, 7);
    }

    /** O agregado só oferece markNotCurrent, que devolve cópia e não altera o original. */
    @Property(tries = 100)
    void markNotCurrentReturnsCopyAndLeavesOriginalUntouched(@ForAll("daysCalculated") int days) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(7);
        h.calculateDays(days);
        for (DailyWaterBalance original : h.balances.snapshot()) {
            DailyWaterBalance copy = original.markNotCurrent();
            assertThat(original.current()).isTrue();
            assertThat(copy.current()).isFalse();
            assertThat(copy).isNotSameAs(original);
            assertThat(copy.inputs()).isSameAs(original.inputs());
            assertThat(copy.result()).isSameAs(original.result());
            assertThat(copy.revision()).isEqualTo(original.revision());
        }
    }

    private static void apply(WaterBalanceServiceHarness h, Change c, int days) {
        switch (c.kind()) {
            case 0 -> h.replacePlanWithFactor(tenths(c.a() % 10 + 1));
            case 1 -> h.plan.updateParameters(h.plan.getAvailabilityFactor(),
                    new SprinklerRate(bd(String.valueOf(c.a() % 50 + 1))));
            case 2 -> h.replaceSoil(String.valueOf(20 + c.a() % 21), String.valueOf(5 + c.b() % 15),
                    "1." + (c.c() % 6));
            case 3 -> h.replaceCrop(tenths(c.a() % 15 + 1), String.valueOf(100 + c.b() % 901));
            case 4 -> h.areas.findById(h.areaId).orElseThrow().update("Área " + c.a(),
                    new AreaInHectares(bd(String.valueOf(c.a()))), null, "alterada",
                    IrrigationSystemType.DRIP, new FlowRate(bd(String.valueOf(c.b()))),
                    new IrrigationEfficiency(bd("0." + (c.c() % 9 + 1))));
            case 5 -> h.setWeather(c.a() % 7 + 1, tenths(c.b() % 200), tenths(c.c() % 300));
            default -> {
                int day = c.a() % 7 + 1;
                h.irrigation.upsert(TestData.appliedIrrigation(h.areaId, TestData.scenarioDate(day),
                        tenths(c.b() % 300)));
            }
        }
    }

    /** Valor com uma casa decimal, {@code n / 10}. */
    private static String tenths(int n) {
        return BigDecimal.valueOf(n, 1).toPlainString();
    }
}
