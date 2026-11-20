package com.irrigation.application.service;

import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 22: histórico de revisões.
 *
 * <p>Depois de calcular alguns dias e reprocessá-los k vezes (a partir de datas arbitrárias), cada data tem
 * tantas revisões quanto o número de vezes em que foi recalculada mais uma. A consulta de revisões devolve todas,
 * da maior para a menor, com exatamente uma corrente (a de maior número) e as anteriores com os valores
 * inalterados. A consulta por período devolve só as correntes, e a consulta de período aberto devolve as
 * correntes de todas as datas da área em ordem cronológica crescente.
 *
 * <p><b>Validates: Requirements 13.6</b>
 */
class RevisionHistoryPropertyTest {

    private static final int MAX_DAYS = 7;

    /** Número de dias calculados e as datas iniciais (1 a days) de cada reprocessamento. */
    private record Scenario(int days, List<Integer> reprocessStarts) {
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        return Arbitraries.integers().between(1, MAX_DAYS).flatMap(days ->
                Arbitraries.integers().between(1, days).list().ofMinSize(0).ofMaxSize(4)
                        .map(starts -> new Scenario(days, starts)));
    }

    /** Período [from, to] em números de dia, podendo passar das pontas dos dias calculados. */
    @Provide
    Arbitrary<int[]> periods() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, MAX_DAYS + 2),
                        Arbitraries.integers().between(0, MAX_DAYS + 2))
                .as((a, len) -> new int[]{a, a + len});
    }

    // Feature: irrigation-water-balance-module, Property 22: Histórico de revisões
    @Property(tries = 100)
    void revisionsAreDescendingWithOneCurrentAndOlderRevisionsIntact(@ForAll("scenarios") Scenario s) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(MAX_DAYS);
        h.calculateDays(s.days());

        int[] expectedCount = new int[s.days() + 1];
        for (int d = 1; d <= s.days(); d++) {
            expectedCount[d] = 1;
        }
        // Primeira versão observada de cada (dia, revisão); nunca pode mudar depois (exceto o indicador).
        Map<String, DailyWaterBalance> firstSeen = new HashMap<>();
        remember(h, s.days(), firstSeen);

        for (int start : s.reprocessStarts()) {
            h.reprocessFrom(start);
            for (int d = start; d <= s.days(); d++) {
                expectedCount[d]++;
            }
            remember(h, s.days(), firstSeen);
        }

        for (int d = 1; d <= s.days(); d++) {
            LocalDate date = TestData.scenarioDate(d);
            int n = expectedCount[d];

            List<WaterBalanceOutput> revisions = h.query.findRevisions(h.areaId, date);
            assertThat(revisions).hasSize(n);
            assertThat(revisions).extracting(r -> r.control().revision())
                    .containsExactlyElementsOf(descending(n));
            assertThat(revisions).extracting(WaterBalanceOutput::date).containsOnly(date);
            assertThat(revisions).extracting(r -> r.control().current())
                    .containsExactly(trueThenFalse(n));

            // Valores das revisões intactos em relação ao que foi observado quando surgiram
            List<DailyWaterBalance> stored = h.balances.findRevisions(h.areaId, date);
            for (DailyWaterBalance b : stored) {
                DailyWaterBalance seen = firstSeen.get(key(d, b.revision()));
                assertThat(seen).isNotNull();
                DailyWaterBalance expected = b.current() ? seen : seen.markNotCurrent();
                assertThat(b).isEqualTo(expected);
                assertThat(b.inputs()).isEqualTo(seen.inputs());
                assertThat(b.result()).isEqualTo(seen.result());
            }

            // A consulta por data devolve a corrente, de maior revisão
            WaterBalanceOutput current = h.query.findByDate(h.areaId, date);
            assertThat(current).isEqualTo(revisions.get(0));
            assertThat(current.control().revision()).isEqualTo(n);
            assertThat(current.control().current()).isTrue();

            // A consulta repetida não altera nada
            assertThat(h.query.findRevisions(h.areaId, date)).isEqualTo(revisions);
        }
    }

    // Feature: irrigation-water-balance-module, Property 22: Histórico de revisões
    @Property(tries = 100)
    void periodQueriesReturnOnlyCurrentRevisionsInAscendingOrder(
            @ForAll("scenarios") Scenario s, @ForAll("periods") int[] period) {
        WaterBalanceServiceHarness h = WaterBalanceServiceHarness.scenarioA(MAX_DAYS);
        h.calculateDays(s.days());
        for (int start : s.reprocessStarts()) {
            h.reprocessFrom(start);
        }
        List<Integer> expectedDays = new ArrayList<>();
        for (int d = 1; d <= s.days(); d++) {
            if (d >= period[0] && d <= period[1]) {
                expectedDays.add(d);
            }
        }

        // Período fechado
        PagedResult<WaterBalanceOutput> closed = h.query.findByPeriod(h.areaId,
                TestData.scenarioDate(period[0]), TestData.scenarioDate(period[1]), 0, 100);
        assertCurrentOnly(h, closed, expectedDays);

        // Período aberto: todas as datas da área
        List<Integer> allDays = new ArrayList<>();
        for (int d = 1; d <= s.days(); d++) {
            allDays.add(d);
        }
        assertCurrentOnly(h, h.query.findByPeriod(h.areaId, null, null, 0, 100), allDays);
    }

    private static void assertCurrentOnly(
            WaterBalanceServiceHarness h, PagedResult<WaterBalanceOutput> result, List<Integer> expectedDays) {
        assertThat(result.totalElements()).isEqualTo(expectedDays.size());
        assertThat(result.content()).extracting(WaterBalanceOutput::date)
                .containsExactlyElementsOf(expectedDays.stream().map(TestData::scenarioDate).toList());
        assertThat(result.content()).allSatisfy(o -> assertThat(o.control().current()).isTrue());
        for (WaterBalanceOutput o : result.content()) {
            assertThat(o).isEqualTo(WaterBalanceOutput.from(h.current(dayNumber(o.date()))));
        }
    }

    private static int dayNumber(LocalDate date) {
        for (int d = 1; d <= MAX_DAYS; d++) {
            if (TestData.scenarioDate(d).equals(date)) {
                return d;
            }
        }
        throw new IllegalArgumentException("data fora do cenário: " + date);
    }

    private static void remember(WaterBalanceServiceHarness h, int days, Map<String, DailyWaterBalance> firstSeen) {
        for (int d = 1; d <= days; d++) {
            DailyWaterBalance c = h.current(d);
            firstSeen.putIfAbsent(key(d, c.revision()), c);
        }
    }

    private static String key(int day, int revision) {
        return day + "/" + revision;
    }

    private static List<Integer> descending(int n) {
        List<Integer> list = new ArrayList<>();
        for (int r = n; r >= 1; r--) {
            list.add(r);
        }
        return list;
    }

    private static Boolean[] trueThenFalse(int n) {
        Boolean[] flags = new Boolean[n];
        for (int i = 0; i < n; i++) {
            flags[i] = i == 0;
        }
        return flags;
    }
}
