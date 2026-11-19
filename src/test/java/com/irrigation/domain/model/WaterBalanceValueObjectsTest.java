package com.irrigation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Limites e escala dos value objects do módulo de balanço hídrico (Req 1.2, 4.3, 4.4, 5.2, 6.3, 7.4).
 * Os limites inferior e superior e o valor imediatamente fora ficam no {@code RangeSpec}, cobertos por
 * {@code NumericValueObjectsTest}. Aqui ficam a escala, os zeros à direita, os limites no passo da própria
 * escala do value object e o {@link CycleDay}.
 */
class WaterBalanceValueObjectsTest {

    /**
     * @param name          nome do value object
     * @param factory       construtor
     * @param maxScale      máximo de casas decimais aceitas
     * @param min           menor valor aceito no passo da escala
     * @param max           limite superior
     * @param justAboveMax  menor valor rejeitado acima do limite, no passo da escala
     * @param justBelowMin  maior valor rejeitado abaixo do limite inferior, no passo da escala
     */
    record ScaleSpec(
            String name,
            Function<BigDecimal, Object> factory,
            int maxScale,
            BigDecimal min,
            BigDecimal max,
            BigDecimal justAboveMax,
            BigDecimal justBelowMin) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static final List<ScaleSpec> SPECS = List.of(
            new ScaleSpec("GravimetricWaterContent", GravimetricWaterContent::new, 2,
                    d("0"), d("100"), d("100.01"), d("-0.01")),
            new ScaleSpec("AvailabilityFactor", AvailabilityFactor::new, 4,
                    d("0.0001"), d("1"), d("1.0001"), d("0")),
            new ScaleSpec("SprinklerRate", SprinklerRate::new, 4,
                    d("0.0001"), d("500"), d("500.0001"), d("0")),
            new ScaleSpec("EtpDepth", EtpDepth::new, 2,
                    d("0"), d("1000"), d("1000.01"), d("-0.01")),
            new ScaleSpec("PrecipitationDepth", PrecipitationDepth::new, 2,
                    d("0"), d("1000"), d("1000.01"), d("-0.01")),
            new ScaleSpec("AppliedIrrigationDepth", AppliedIrrigationDepth::new, 2,
                    d("0"), d("1000"), d("1000.01"), d("-0.01")));

    @TestFactory
    Stream<DynamicTest> acceptBoundsInTheOwnScaleStep() {
        return SPECS.stream().flatMap(s -> Stream.of(
                DynamicTest.dynamicTest(s + " aceita o menor valor no passo da escala",
                        () -> assertThatCode(() -> s.factory().apply(s.min())).doesNotThrowAnyException()),
                DynamicTest.dynamicTest(s + " aceita o limite superior",
                        () -> assertThatCode(() -> s.factory().apply(s.max())).doesNotThrowAnyException())));
    }

    @TestFactory
    Stream<DynamicTest> rejectJustOutsideInTheOwnScaleStep() {
        return SPECS.stream().flatMap(s -> Stream.of(
                DynamicTest.dynamicTest(s + " rejeita imediatamente abaixo do limite inferior",
                        () -> assertThatThrownBy(() -> s.factory().apply(s.justBelowMin()))
                                .isInstanceOf(IllegalArgumentException.class)),
                DynamicTest.dynamicTest(s + " rejeita imediatamente acima do limite superior",
                        () -> assertThatThrownBy(() -> s.factory().apply(s.justAboveMax()))
                                .isInstanceOf(IllegalArgumentException.class))));
    }

    @TestFactory
    Stream<DynamicTest> acceptUpToMaxScale() {
        return SPECS.stream().map(s -> {
            BigDecimal atScale = BigDecimal.ONE.movePointLeft(s.maxScale()).add(BigDecimal.ONE.movePointLeft(s.maxScale()));
            return DynamicTest.dynamicTest(s + " aceita " + s.maxScale() + " casas decimais",
                    () -> assertThatCode(() -> s.factory().apply(atScale)).doesNotThrowAnyException());
        });
    }

    @TestFactory
    Stream<DynamicTest> rejectMoreThanMaxScale() {
        return SPECS.stream().map(s -> {
            BigDecimal tooPrecise = d("0.5").add(BigDecimal.ONE.movePointLeft(s.maxScale() + 1));
            return DynamicTest.dynamicTest(s + " rejeita " + (s.maxScale() + 1) + " casas decimais",
                    () -> assertThatThrownBy(() -> s.factory().apply(tooPrecise))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(s.maxScale() + " casas"));
        });
    }

    @TestFactory
    Stream<DynamicTest> acceptTrailingZerosBeyondMaxScale() {
        return SPECS.stream().map(s -> {
            // 0,5 escrito com casas a mais que o máximo, só com zeros à direita
            BigDecimal padded = new BigDecimal("0.5").setScale(s.maxScale() + 3);
            return DynamicTest.dynamicTest(s + " aceita zeros à direita além da escala máxima",
                    () -> assertThatCode(() -> s.factory().apply(padded)).doesNotThrowAnyException());
        });
    }

    @TestFactory
    Stream<DynamicTest> acceptZeroWithAnyScale() {
        return SPECS.stream()
                .filter(s -> s.min().signum() == 0)
                .map(s -> DynamicTest.dynamicTest(s + " aceita zero escrito com muitas casas",
                        () -> assertThatCode(() -> s.factory().apply(new BigDecimal("0.000000")))
                                .doesNotThrowAnyException()));
    }

    @TestFactory
    Stream<DynamicTest> rejectNull() {
        return SPECS.stream().map(s -> DynamicTest.dynamicTest(s + " rejeita nulo",
                () -> assertThatThrownBy(() -> s.factory().apply(null)).isInstanceOf(NullPointerException.class)));
    }

    @Test
    void positiveOnlyValueObjectsRejectZero() {
        assertThatThrownBy(() -> new AvailabilityFactor(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SprinklerRate(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availabilityFactorIsKeptAsInformedWithoutDividingBy100() {
        assertThat(new AvailabilityFactor(new BigDecimal("0.50")).value()).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> new AvailabilityFactor(new BigDecimal("50")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- CycleDay (A-04: inteiro >= 1, sem limite superior) ---

    @Test
    void cycleDayAcceptsLowerBound() {
        assertThat(new CycleDay(1).value()).isEqualTo(1);
    }

    @Test
    void cycleDayRejectsImmediatelyBelowLowerBound() {
        assertThatThrownBy(() -> new CycleDay(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CycleDay(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CycleDay(Integer.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cycleDayHasNoUpperLimit() {
        assertThat(new CycleDay(2).value()).isEqualTo(2);
        assertThat(new CycleDay(Integer.MAX_VALUE).value()).isEqualTo(Integer.MAX_VALUE);
    }
}
