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

import java.math.BigDecimal;

/**
 * Geradores jqwik compartilhados dos testes de propriedade do balanço hídrico.
 * Produzem apenas entradas válidas, com as escalas permitidas e valores nas bordas das faixas.
 */
final class WaterBalanceArbitraries {

    private WaterBalanceArbitraries() {
    }

    /** Decimal entre 0 e 1000 (inclusive), escala 2, com bordas frequentes. */
    static Arbitrary<BigDecimal> depthMm() {
        Arbitrary<BigDecimal> any = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000")).ofScale(2);
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(8, any),
                net.jqwik.api.Tuple.of(1, Arbitraries.of(
                        new BigDecimal("0"), new BigDecimal("0.01"), new BigDecimal("1000.00"))));
    }

    /** KC no intervalo (0, 2,0], escala 2. */
    static Arbitrary<BigDecimal> kc() {
        return Arbitraries.integers().between(1, 200).map(i -> BigDecimal.valueOf(i, 2));
    }

    /** F no intervalo (0, 1], escala 2. */
    static Arbitrary<BigDecimal> availabilityFactor() {
        return Arbitraries.integers().between(1, 100).map(i -> BigDecimal.valueOf(i, 2));
    }

    /** Taxa de aplicação em (0, 500], escala 4. */
    static Arbitrary<BigDecimal> sprinklerRate() {
        return Arbitraries.integers().between(1, 5_000_000).map(i -> BigDecimal.valueOf(i, 4));
    }

    /** Entradas válidas do calculador, para primeiro dia ou dia seguinte (AFDf anterior arbitrária). */
    static Arbitrary<WaterBalanceInputs> inputs(boolean firstDay) {
        Arbitrary<BigDecimal> previousAfdFinal = Arbitraries.bigDecimals()
                .between(new BigDecimal("-5000"), new BigDecimal("5000")).ofScale(10);
        return Combinators.combine(
                        depthMm(), depthMm(), depthMm(), kc(), availabilityFactor(), sprinklerRate(),
                        previousAfdFinal)
                .as((etp, p, ir, kc, f, rate, prev) -> new WaterBalanceInputs(
                        new EtpDepth(etp),
                        new PrecipitationDepth(p),
                        new AppliedIrrigationDepth(ir),
                        new GravimetricWaterContent(new BigDecimal("30")),
                        new GravimetricWaterContent(new BigDecimal("15")),
                        new BulkDensity(new BigDecimal("1.2")),
                        new RootDepth(new BigDecimal("400")),
                        new Kc(kc),
                        new AvailabilityFactor(f),
                        new SprinklerRate(rate),
                        firstDay,
                        firstDay ? null : prev));
    }
}
