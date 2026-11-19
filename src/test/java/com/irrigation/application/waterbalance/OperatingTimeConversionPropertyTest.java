package com.irrigation.application.waterbalance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Teste de propriedade da conversão de horas persistidas em horas e minutos.
 */
class OperatingTimeConversionPropertyTest {

    // Feature: irrigation-water-balance-module, Property 7: Conversão para horas e minutos
    /**
     * Para qualquer horas persistidas (escala 10), os minutos ficam entre 0 e 59 e o total
     * em minutos equivale a round(horas × 60, HALF_UP) sobre o valor sem arredondamento prévio.
     *
     * <p><b>Valida: Requisitos 10.3, 15.6</b>
     */
    @Property(tries = 500)
    void conversionMatchesIndependentRounding(@ForAll("persistedHours") BigDecimal hours) {
        PresentationRounding.OperatingTimeView view = PresentationRounding.operatingTime(hours);

        long expectedTotal = hours.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        assertThat(view.wholeHours()).isGreaterThanOrEqualTo(0);
        assertThat(view.minutes()).isBetween(0, 59);
        assertThat((long) view.wholeHours() * 60 + view.minutes()).isEqualTo(expectedTotal);
        assertThat(view.hours()).isEqualTo(hours.setScale(4, RoundingMode.HALF_UP));
    }

    // Feature: irrigation-water-balance-module, Property 7: Conversão para horas e minutos
    /** Zero horas gera 0 h 0 min. */
    @Property(tries = 100)
    void zeroGeneratesZeroHoursZeroMinutes(@ForAll("zeroHours") BigDecimal zero) {
        PresentationRounding.OperatingTimeView view = PresentationRounding.operatingTime(zero);

        assertThat(view.wholeHours()).isZero();
        assertThat(view.minutes()).isZero();
        assertThat(view.hours().signum()).isZero();
    }

    @Provide
    Arbitrary<BigDecimal> persistedHours() {
        Arbitrary<BigDecimal> random = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(200))
                .ofScale(10);
        // Casos próximos da fronteira de arredondamento dos minutos (x,5 min) e de 59,5 min.
        Arbitrary<BigDecimal> borders = Arbitraries.integers().between(0, 12_000)
                .map(minutes -> BigDecimal.valueOf(minutes * 2L + 1)
                        .divide(BigDecimal.valueOf(120), 10, RoundingMode.HALF_UP));
        return Arbitraries.oneOf(random, borders);
    }

    @Provide
    Arbitrary<BigDecimal> zeroHours() {
        return Arbitraries.just(BigDecimal.ZERO.setScale(10));
    }
}
