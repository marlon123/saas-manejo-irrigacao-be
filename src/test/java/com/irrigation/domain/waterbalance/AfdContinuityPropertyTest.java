package com.irrigation.domain.waterbalance;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 4: Continuidade da AFDi em sequências de dias.
 *
 * <p>Para uma sequência de 1 a 30 dias consecutivos com o mesmo solo, a mesma cultura e o mesmo F,
 * a AFDi de cada dia posterior ao primeiro é igual, sem arredondamento ({@code compareTo}), à AFDf do
 * dia anterior, e a AFDi do primeiro dia é {@code CAD × F}. A AFDf não é limitada (pode ser negativa
 * ou maior que a CAD). O TR (tempo de rega) varia livremente, inclusive voltando a 1, e não influencia
 * o calculador: ele só acompanha o dia no gerador.
 *
 * <p><b>Validates: Requirements 8.2, 8.3, 15.8</b>
 */
class AfdContinuityPropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    /** Entradas variáveis de um dia. {@code tr} é o turno de rega, ignorado pelo calculador. */
    private record Day(int tr, BigDecimal etp, BigDecimal precipitation, BigDecimal irrigation,
                       BigDecimal kc, BigDecimal sprinklerRate) {
    }

    /** Parâmetros fixos ao longo da sequência. */
    private record Plan(BigDecimal fieldCapacity, BigDecimal wiltingPoint, BigDecimal bulkDensity,
                        BigDecimal rootDepth, BigDecimal factor) {
    }

    private static Arbitrary<BigDecimal> decimals(long min, long max, int scale) {
        return Arbitraries.longs().between(min, max).map(v -> BigDecimal.valueOf(v, scale));
    }

    @Provide
    Arbitrary<Plan> plan() {
        Arbitrary<BigDecimal[]> moisture = Combinators.combine(
                decimals(0, 10_000, 2), decimals(0, 10_000, 2))
                .as((a, b) -> a.compareTo(b) >= 0 ? new BigDecimal[] {a, b} : new BigDecimal[] {b, a});
        return Combinators.combine(
                moisture,
                decimals(1, 300, 2),       // D (0, 3]
                decimals(1, 500_000, 2),   // Z (0, 5000]
                decimals(1, 10_000, 4))    // F (0, 1]
                .as((m, d, z, f) -> new Plan(m[0], m[1], d, z, f));
    }

    @Provide
    Arbitrary<List<Day>> days() {
        // TR de 1 a 10, sorteado sem relação com o dia anterior (volta a 1 com frequência)
        Arbitrary<Integer> tr = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, Arbitraries.just(1)),
                net.jqwik.api.Tuple.of(7, Arbitraries.integers().between(1, 10)));
        return Combinators.combine(
                tr,
                decimals(0, 100_000, 2),   // ETP 0 a 1000 mm
                decimals(0, 100_000, 2),   // P 0 a 1000 mm
                decimals(0, 100_000, 2),   // IR 0 a 1000 mm
                decimals(1, 200, 2),       // KC (0, 2]
                decimals(1, 5_000_000, 4)) // taxa (0, 500]
                .as(Day::new)
                .list().ofMinSize(1).ofMaxSize(30);
    }

    private WaterBalanceInputs inputs(Plan p, Day d, BigDecimal previousAfdFinal) {
        return new WaterBalanceInputs(
                new EtpDepth(d.etp()),
                new PrecipitationDepth(d.precipitation()),
                new AppliedIrrigationDepth(d.irrigation()),
                new GravimetricWaterContent(p.fieldCapacity()),
                new GravimetricWaterContent(p.wiltingPoint()),
                new BulkDensity(p.bulkDensity()),
                new RootDepth(p.rootDepth()),
                new Kc(d.kc()),
                new AvailabilityFactor(p.factor()),
                new SprinklerRate(d.sprinklerRate()),
                previousAfdFinal == null,
                previousAfdFinal);
    }

    private List<WaterBalanceResult> run(Plan plan, List<Day> days) {
        List<WaterBalanceResult> results = new java.util.ArrayList<>();
        BigDecimal previous = null;
        for (Day day : days) {
            WaterBalanceResult result = calculator.calculate(inputs(plan, day, previous));
            results.add(result);
            previous = result.afdFinal();
        }
        return results;
    }

    // Feature: irrigation-water-balance-module, Property 4: Continuidade da AFDi em sequências de dias
    @Property(tries = 200)
    void afdInitialOfEachDayEqualsPreviousAfdFinal(
            @ForAll("plan") Plan plan,
            @ForAll("days") List<Day> days) {

        List<WaterBalanceResult> results = run(plan, days);
        assertThat(results).hasSameSizeAs(days);

        // AFDi do dia 1 = CAD × F, com CAD calculada de forma independente
        BigDecimal expectedCad = new BigDecimal("0.01")
                .multiply(plan.fieldCapacity().subtract(plan.wiltingPoint()))
                .multiply(plan.bulkDensity())
                .multiply(plan.rootDepth());
        assertThat(results.get(0).afdInitial().compareTo(expectedCad.multiply(plan.factor()))).isZero();

        BigDecimal expectedAfdi = results.get(0).afdInitial();
        for (int i = 0; i < results.size(); i++) {
            Day day = days.get(i);
            WaterBalanceResult result = results.get(i);

            // continuidade exata: AFDi(n+1) = AFDf(n), sem arredondamento
            if (i > 0) {
                assertThat(result.afdInitial().compareTo(results.get(i - 1).afdFinal()))
                        .as("AFDi do dia %d", i + 1).isZero();
            }

            // AFDf = AFDi + P + IR - ETP×KC, sem limitar (Req 8.3), recalculada de forma independente
            BigDecimal etc = day.etp().multiply(day.kc());
            expectedAfdi = result.afdInitial();
            BigDecimal expectedFinal = expectedAfdi
                    .add(day.precipitation()).add(day.irrigation()).subtract(etc);
            assertThat(result.afdFinal().compareTo(expectedFinal))
                    .as("AFDf do dia %d (sem limitação nem arredondamento)", i + 1).isZero();
        }
    }

    // Feature: irrigation-water-balance-module, Property 4: Continuidade da AFDi em sequências de dias
    @Property(tries = 100)
    void trDoesNotInfluenceTheChain(
            @ForAll("plan") Plan plan,
            @ForAll("days") List<Day> days) {

        // a mesma sequência com TR diferente (todos 1) produz exatamente os mesmos valores
        List<Day> allOnes = days.stream()
                .map(d -> new Day(1, d.etp(), d.precipitation(), d.irrigation(), d.kc(), d.sprinklerRate()))
                .toList();

        List<WaterBalanceResult> original = run(plan, days);
        List<WaterBalanceResult> reset = run(plan, allOnes);

        for (int i = 0; i < original.size(); i++) {
            assertThat(reset.get(i).afdInitial().compareTo(original.get(i).afdInitial())).isZero();
            assertThat(reset.get(i).afdFinal().compareTo(original.get(i).afdFinal())).isZero();
        }
    }
}
