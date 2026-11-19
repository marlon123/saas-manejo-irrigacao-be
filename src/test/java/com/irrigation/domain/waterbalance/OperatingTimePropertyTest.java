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
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de propriedade do tempo de funcionamento (Operating_Time).
 *
 * <p>**Validates: Requirements 10.1, 15.3**
 */
class OperatingTimePropertyTest {

    private static final int SCALE = 10;
    private static final BigDecimal HALF_ULP = new BigDecimal("0.00000000005");

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    /** Entradas de um dia, com o contexto do primeiro dia ou não. */
    record Sample(BigDecimal kc, BigDecimal etp, BigDecimal precipitation, BigDecimal irrigation,
                  BigDecimal previousAfd, BigDecimal rate, boolean firstDay) {
    }

    @Provide
    Arbitrary<Sample> samples() {
        return Combinators.combine(
                        scaled(1, 200),            // Kc em (0, 2,00]
                        scaled(0, 100_000),        // ETP em [0, 1000]
                        scaled(0, 50_000),         // P em [0, 500]
                        scaled(0, 50_000),         // IR em [0, 500]
                        Arbitraries.longs().between(-100_000, 100_000).map(u -> BigDecimal.valueOf(u, 2)),
                        scaled(1, 50_000),         // taxa em (0, 500]
                        Arbitraries.of(true, false))
                .as(Sample::new);
    }

    private static Arbitrary<BigDecimal> scaled(long minUnits, long maxUnits) {
        return Arbitraries.longs().between(minUnits, maxUnits).map(u -> BigDecimal.valueOf(u, 2));
    }

    private WaterBalanceResult run(Sample s) {
        WaterBalanceInputs inputs = new WaterBalanceInputs(
                new EtpDepth(s.etp()),
                new PrecipitationDepth(s.precipitation()),
                new AppliedIrrigationDepth(s.irrigation()),
                new GravimetricWaterContent(new BigDecimal("30.00")),
                new GravimetricWaterContent(new BigDecimal("15.00")),
                new BulkDensity(new BigDecimal("1.30")),
                new RootDepth(new BigDecimal("500.00")),
                new Kc(s.kc()),
                new AvailabilityFactor(new BigDecimal("0.5")),
                new SprinklerRate(s.rate()),
                s.firstDay(),
                s.firstDay() ? null : s.previousAfd());
        return calculator.calculate(inputs);
    }

    // Feature: irrigation-water-balance-module, Property 6: Tempo de funcionamento
    @Property(tries = 300)
    void operatingTimeIsIdealIrrigationOverSprinklerRate(@ForAll("samples") Sample s) {
        WaterBalanceResult result = run(s);
        BigDecimal time = result.operatingTimeHours();

        assertThat(time.scale()).isEqualTo(SCALE);
        assertThat(time.signum()).isGreaterThanOrEqualTo(0);

        if (result.recommended()) {
            BigDecimal expected = result.idealIrrigation().divide(s.rate(), SCALE, RoundingMode.HALF_EVEN);
            assertThat(time).isEqualTo(expected);
            // tempo × taxa reconstitui I com erro de no máximo meio ulp (10^-10) × taxa
            BigDecimal error = time.multiply(s.rate()).subtract(result.idealIrrigation()).abs();
            assertThat(error).isLessThanOrEqualTo(HALF_ULP.multiply(s.rate()));
        } else {
            assertThat(time.compareTo(BigDecimal.ZERO)).isZero();
        }
    }
}
