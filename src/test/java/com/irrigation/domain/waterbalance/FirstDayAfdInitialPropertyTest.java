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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedade 3: AFDi do primeiro dia.
 *
 * <p>Para qualquer entrada válida no primeiro dia do plano, {@code AFDi = CAD × F}, com F em (0, 1]
 * usado diretamente (sem dividir por 100). A comparação é exata ({@code compareTo}). O valor da AFDf
 * anterior não influencia o resultado: o primeiro dia só aceita {@code previousAfdFinal} nulo, e a
 * AFDi não muda quando variam as entradas do dia (ETP, P, IR, KC e taxa do aspersor).
 *
 * <p><b>Validates: Requirements 8.1</b>
 */
class FirstDayAfdInitialPropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    /** Parâmetros do dia que não deveriam influenciar a AFDi do primeiro dia. */
    private record DailyInputs(BigDecimal etp, BigDecimal precipitation, BigDecimal irrigation,
                               BigDecimal kc, BigDecimal sprinklerRate) {
    }

    /** Parâmetros do solo, da cultura e do fator F. */
    private record SoilAndFactor(BigDecimal fieldCapacity, BigDecimal wiltingPoint,
                                 BigDecimal bulkDensity, BigDecimal rootDepth, BigDecimal factor) {
    }

    private static BigDecimal scaled(long unscaled, int scale) {
        return BigDecimal.valueOf(unscaled, scale);
    }

    private static Arbitrary<BigDecimal> decimals(long min, long max, int scale) {
        return Arbitraries.longs().between(min, max).map(v -> scaled(v, scale));
    }

    @Provide
    Arbitrary<DailyInputs> dailyInputs() {
        return Combinators.combine(
                decimals(0, 100_000, 2),   // ETP 0 a 1000 mm
                decimals(0, 100_000, 2),   // P 0 a 1000 mm
                decimals(0, 100_000, 2),   // IR 0 a 1000 mm
                decimals(1, 200, 2),       // KC (0, 2]
                decimals(1, 5_000_000, 4)) // taxa do aspersor (0, 500]
                .as(DailyInputs::new);
    }

    @Provide
    Arbitrary<SoilAndFactor> soilAndFactor() {
        Arbitrary<BigDecimal[]> moisture = Combinators.combine(
                decimals(0, 10_000, 2), decimals(0, 10_000, 2))
                .as((a, b) -> a.compareTo(b) >= 0
                        ? new BigDecimal[] {a, b}
                        : new BigDecimal[] {b, a});
        return Combinators.combine(
                moisture,
                decimals(1, 300, 2),        // D (0, 3]
                decimals(1, 500_000, 2),    // Z (0, 5000]
                decimals(1, 10_000, 4))     // F (0, 1], 4 casas
                .as((m, d, z, f) -> new SoilAndFactor(m[0], m[1], d, z, f));
    }

    private WaterBalanceInputs inputs(SoilAndFactor s, DailyInputs d, BigDecimal previousAfdFinal) {
        boolean first = previousAfdFinal == null;
        return new WaterBalanceInputs(
                new EtpDepth(d.etp()),
                new PrecipitationDepth(d.precipitation()),
                new AppliedIrrigationDepth(d.irrigation()),
                new GravimetricWaterContent(s.fieldCapacity()),
                new GravimetricWaterContent(s.wiltingPoint()),
                new BulkDensity(s.bulkDensity()),
                new RootDepth(s.rootDepth()),
                new Kc(d.kc()),
                new AvailabilityFactor(s.factor()),
                new SprinklerRate(d.sprinklerRate()),
                first,
                previousAfdFinal);
    }

    // Feature: irrigation-water-balance-module, Property 3: AFDi do primeiro dia
    @Property(tries = 200)
    void firstDayAfdInitialEqualsCadTimesFactor(
            @ForAll("soilAndFactor") SoilAndFactor soil,
            @ForAll("dailyInputs") DailyInputs day,
            @ForAll("dailyInputs") DailyInputs otherDay) {

        WaterBalanceResult result = calculator.calculate(inputs(soil, day, null));

        // CAD calculada de forma independente da implementação
        BigDecimal expectedCad = new BigDecimal("0.01")
                .multiply(soil.fieldCapacity().subtract(soil.wiltingPoint()))
                .multiply(soil.bulkDensity())
                .multiply(soil.rootDepth());
        BigDecimal expectedAfdi = expectedCad.multiply(soil.factor());

        assertThat(result.cad().compareTo(expectedCad)).isZero();
        assertThat(result.afdInitial().compareTo(expectedAfdi)).isZero();
        assertThat(result.afdInitial().compareTo(result.cad().multiply(soil.factor()))).isZero();

        // F é usado como fração (0 a 1), nunca dividido por 100
        assertThat(result.afdInitial().compareTo(result.cad())).isLessThanOrEqualTo(0);

        // a AFDi não depende das entradas do dia
        WaterBalanceResult other = calculator.calculate(inputs(soil, otherDay, null));
        assertThat(other.afdInitial().compareTo(result.afdInitial())).isZero();
    }

    // Feature: irrigation-water-balance-module, Property 3: AFDi do primeiro dia
    @Property(tries = 100)
    void previousAfdFinalCannotInfluenceFirstDay(
            @ForAll("soilAndFactor") SoilAndFactor soil,
            @ForAll("dailyInputs") DailyInputs day,
            @ForAll("previousValues") BigDecimal previous) {

        // no primeiro dia, uma AFDf anterior é recusada e, portanto, nunca entra no cálculo
        assertThatThrownBy(() -> new WaterBalanceInputs(
                new EtpDepth(day.etp()),
                new PrecipitationDepth(day.precipitation()),
                new AppliedIrrigationDepth(day.irrigation()),
                new GravimetricWaterContent(soil.fieldCapacity()),
                new GravimetricWaterContent(soil.wiltingPoint()),
                new BulkDensity(soil.bulkDensity()),
                new RootDepth(soil.rootDepth()),
                new Kc(day.kc()),
                new AvailabilityFactor(soil.factor()),
                new SprinklerRate(day.sprinklerRate()),
                true,
                previous))
                .isInstanceOf(IllegalArgumentException.class);

        // e o resultado do primeiro dia é o mesmo, qualquer que seja o valor anterior disponível
        WaterBalanceResult first = calculator.calculate(inputs(soil, day, null));
        BigDecimal expected = first.cad().multiply(soil.factor());
        assertThat(first.afdInitial().compareTo(expected)).isZero();
    }

    @Provide
    Arbitrary<BigDecimal> previousValues() {
        return decimals(-100_000_000, 100_000_000, 6);
    }
}
