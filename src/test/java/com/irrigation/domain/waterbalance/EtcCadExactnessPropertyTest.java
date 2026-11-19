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

/**
 * Teste de propriedade das fórmulas exatas de ETc e CAD.
 *
 * <p>**Validates: Requirements 7.1, 15.1, 15.2**
 */
class EtcCadExactnessPropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    /** Entradas de um dia: valores brutos para ETc e CAD, mais o contexto do primeiro dia ou não. */
    record Sample(BigDecimal kc, BigDecimal etp, BigDecimal cc, BigDecimal pmp,
                  BigDecimal density, BigDecimal depth, boolean firstDay) {
    }

    @Provide
    Arbitrary<Sample> samples() {
        return Combinators.combine(
                        WaterBalanceArbitraries.kc(),
                        scaled(0, 100_000),      // ETP em [0, 1000]
                        fieldCapacityAndWiltingPoint(),
                        scaled(1, 300),          // D em (0, 3,00]
                        scaled(1, 500_000),      // Z em (0, 5000,00]
                        Arbitraries.of(true, false))
                .as((kc, etp, pair, d, z, first) -> new Sample(kc, etp, pair[0], pair[1], d, z, first));
    }

    private static Arbitrary<BigDecimal> scaled(long minUnits, long maxUnits) {
        return Arbitraries.longs().between(minUnits, maxUnits).map(u -> BigDecimal.valueOf(u, 2));
    }

    /** Par (CC, PMP) em [0, 100] com CC estritamente maior que PMP. */
    private static Arbitrary<BigDecimal[]> fieldCapacityAndWiltingPoint() {
        return Arbitraries.longs().between(0, 9_999).flatMap(pmp ->
                Arbitraries.longs().between(pmp + 1, 10_000).map(cc ->
                        new BigDecimal[]{BigDecimal.valueOf(cc, 2), BigDecimal.valueOf(pmp, 2)}));
    }

    // Feature: irrigation-water-balance-module, Property 1: Fórmulas exatas de ETc e CAD
    @Property(tries = 200)
    void etcAndCadFollowExactFormulas(@ForAll("samples") Sample s) {
        WaterBalanceInputs inputs = new WaterBalanceInputs(
                new EtpDepth(s.etp()),
                new PrecipitationDepth(BigDecimal.ZERO),
                new AppliedIrrigationDepth(BigDecimal.ZERO),
                new GravimetricWaterContent(s.cc()),
                new GravimetricWaterContent(s.pmp()),
                new BulkDensity(s.density()),
                new RootDepth(s.depth()),
                new Kc(s.kc()),
                new AvailabilityFactor(new BigDecimal("0.5")),
                new SprinklerRate(new BigDecimal("6")),
                s.firstDay(),
                s.firstDay() ? null : BigDecimal.TEN);

        WaterBalanceResult result = calculator.calculate(inputs);

        BigDecimal expectedEtc = s.kc().multiply(s.etp());
        BigDecimal expectedCad = new BigDecimal("0.01")
                .multiply(s.cc().subtract(s.pmp()))
                .multiply(s.density())
                .multiply(s.depth());

        assertThat(result.etc()).isEqualByComparingTo(expectedEtc);
        assertThat(result.cad()).isEqualByComparingTo(expectedCad);
        assertThat(result.etc().compareTo(expectedEtc)).isZero();
        assertThat(result.cad().compareTo(expectedCad)).isZero();
        assertThat(result.cad().signum()).isPositive();
    }
}
