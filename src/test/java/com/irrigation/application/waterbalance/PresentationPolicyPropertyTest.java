package com.irrigation.application.waterbalance;

import static org.assertj.core.api.Assertions.assertThat;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.waterbalance.WaterBalanceCalculator;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Testes de propriedade da política única de apresentação (mm com 2 casas e horas com 4 casas,
 * ambas {@code HALF_UP}), de sua idempotência e da separação entre valor persistido e apresentado.
 */
class PresentationPolicyPropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * Todo valor em mm sai com escala 2 e {@code HALF_UP} (metades se afastam de zero), igual ao
     * resultado de um oráculo independente baseado no valor absoluto.
     *
     * <p><b>Valida: Requisitos 15.4, 15.7</b>
     */
    @Property(tries = 500)
    void mmUsesScaleTwoHalfUp(@ForAll("anyValues") BigDecimal value) {
        BigDecimal rounded = PresentationRounding.roundMm(value);

        assertThat(rounded.scale()).isEqualTo(2);
        assertThat(rounded).isEqualTo(oracle(value, 2));
    }

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * Metades exatas (x,xx5) positivas e negativas sempre se afastam de zero.
     *
     * <p><b>Valida: Requisitos 15.4, 15.7</b>
     */
    @Property(tries = 300)
    void mmHalvesRoundAwayFromZero(@ForAll("halvesMm") BigDecimal half) {
        BigDecimal rounded = PresentationRounding.roundMm(half);
        BigDecimal step = new BigDecimal("0.005");

        assertThat(rounded.scale()).isEqualTo(2);
        if (half.signum() > 0) {
            assertThat(rounded).isEqualTo(half.add(step).setScale(2, RoundingMode.UNNECESSARY));
        } else {
            assertThat(rounded).isEqualTo(half.subtract(step).setScale(2, RoundingMode.UNNECESSARY));
        }
    }

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * Horas saem com escala 4 e {@code HALF_UP}, tanto em roundHours quanto na visão do tempo
     * de funcionamento.
     *
     * <p><b>Valida: Requisitos 15.5, 15.7</b>
     */
    @Property(tries = 500)
    void hoursUseScaleFourHalfUp(@ForAll("persistedHours") BigDecimal hours) {
        BigDecimal rounded = PresentationRounding.roundHours(hours);

        assertThat(rounded.scale()).isEqualTo(4);
        assertThat(rounded).isEqualTo(oracle(hours, 4));
        assertThat(PresentationRounding.operatingTime(hours).hours()).isEqualTo(rounded);
    }

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * Aplicar a política duas vezes dá o mesmo valor que aplicá-la uma vez.
     *
     * <p><b>Valida: Requisitos 15.7</b>
     */
    @Property(tries = 500)
    void policyIsIdempotent(
            @ForAll("anyValues") BigDecimal mm, @ForAll("persistedHours") BigDecimal hours) {
        BigDecimal once = PresentationRounding.roundMm(mm);
        assertThat(PresentationRounding.roundMm(once)).isEqualTo(once);

        BigDecimal onceHours = PresentationRounding.roundHours(hours);
        assertThat(PresentationRounding.roundHours(onceHours)).isEqualTo(onceHours);
    }

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * A apresentação não altera o valor persistido (mesmo valor e mesma escala) e devolve outra
     * instância apenas quando o arredondamento muda alguma coisa.
     *
     * <p><b>Valida: Requisitos 15.8, 14.6</b>
     */
    @Property(tries = 300)
    void presentationDoesNotChangePersistedValue(
            @ForAll("anyValues") BigDecimal persisted, @ForAll("persistedHours") BigDecimal hours) {
        BigDecimal persistedBefore = new BigDecimal(persisted.unscaledValue(), persisted.scale());
        BigDecimal hoursBefore = new BigDecimal(hours.unscaledValue(), hours.scale());

        PresentationRounding.roundMm(persisted);
        PresentationRounding.roundHours(hours);
        PresentationRounding.operatingTime(hours);

        assertThat(persisted).isEqualTo(persistedBefore);
        assertThat(persisted.scale()).isEqualTo(persistedBefore.scale());
        assertThat(hours).isEqualTo(hoursBefore);
        assertThat(hours.scale()).isEqualTo(hoursBefore.scale());
    }

    // Feature: irrigation-water-balance-module, Property 8: Política de apresentação consistente
    /**
     * O dia seguinte parte da AFDf persistida, não da apresentada. Quando os dois valores diferem,
     * a AFDi e a AFDf do dia seguinte refletem a diferença exata.
     *
     * <p><b>Valida: Requisitos 15.8, 14.6</b>
     */
    @Property(tries = 300)
    void nextDayUsesPersistedAfdNotPresented(
            @ForAll("firstDayInputs") WaterBalanceInputs firstDay,
            @ForAll("dailyDepth") BigDecimal etp) {
        WaterBalanceResult day1 = calculator.calculate(firstDay);
        BigDecimal persistedAfd = day1.afdFinal();
        BigDecimal presentedAfd = PresentationRounding.roundMm(persistedAfd);
        BigDecimal persistedSnapshot = new BigDecimal(persistedAfd.unscaledValue(), persistedAfd.scale());

        WaterBalanceResult withPersisted = calculator.calculate(nextDay(firstDay, etp, persistedAfd));
        WaterBalanceResult withPresented = calculator.calculate(nextDay(firstDay, etp, presentedAfd));

        // O cálculo encadeado usa exatamente o valor persistido.
        assertThat(withPersisted.afdInitial()).isEqualTo(persistedAfd);
        assertThat(day1.afdFinal()).isEqualTo(persistedSnapshot);

        BigDecimal difference = persistedAfd.subtract(presentedAfd);
        // Sempre vale: a AFDf encadeada difere exatamente do erro de apresentação.
        assertThat(withPersisted.afdFinal().subtract(withPresented.afdFinal()))
                .isEqualByComparingTo(difference);

        if (difference.signum() != 0) {
            assertThat(withPersisted.afdInitial()).isNotEqualByComparingTo(withPresented.afdInitial());
            assertThat(withPersisted.afdFinal()).isNotEqualByComparingTo(withPresented.afdFinal());
        }
    }

    /** Oráculo independente: HALF_UP sobre o módulo, com o sinal reaplicado. */
    private static BigDecimal oracle(BigDecimal value, int scale) {
        BigDecimal magnitude = value.abs().setScale(scale, RoundingMode.HALF_UP);
        return value.signum() < 0 ? magnitude.negate() : magnitude;
    }

    private static WaterBalanceInputs nextDay(WaterBalanceInputs base, BigDecimal etp, BigDecimal previousAfd) {
        return new WaterBalanceInputs(
                new EtpDepth(etp),
                base.precipitation(),
                base.appliedIrrigation(),
                base.fieldCapacity(),
                base.permanentWiltingPoint(),
                base.bulkDensity(),
                base.rootDepth(),
                base.kc(),
                base.availabilityFactor(),
                base.sprinklerRate(),
                false,
                previousAfd);
    }

    @Provide
    Arbitrary<BigDecimal> anyValues() {
        // Escalas de 0 a 10, positivos e negativos, inclusive metades.
        Arbitrary<BigDecimal> random = Combinators.combine(
                        Arbitraries.longs().between(-100_000_000_000L, 100_000_000_000L),
                        Arbitraries.integers().between(0, 10))
                .as((unscaled, scale) -> BigDecimal.valueOf(unscaled, scale));
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(4, random),
                net.jqwik.api.Tuple.of(2, halvesMm()),
                net.jqwik.api.Tuple.of(1, Arbitraries.of(BigDecimal.ZERO, new BigDecimal("0.005"),
                        new BigDecimal("-0.005"), new BigDecimal("0.00"), new BigDecimal("-0.004"))));
    }

    @Provide
    Arbitrary<BigDecimal> halvesMm() {
        // x,xx5 com sinal positivo ou negativo.
        return Arbitraries.longs().between(-1_000_000L, 1_000_000L)
                .map(n -> BigDecimal.valueOf(n * 10 + (n < 0 ? -5 : 5), 3));
    }

    @Provide
    Arbitrary<BigDecimal> persistedHours() {
        // Horas persistidas com escala 10, inclusive metades de 4 casas (x,xxxx5).
        Arbitrary<BigDecimal> scale10 = Arbitraries.longs().between(0L, 2_400_000_000_000L)
                .map(n -> BigDecimal.valueOf(n, 10));
        Arbitrary<BigDecimal> halves = Arbitraries.longs().between(0L, 2_400_000L)
                .map(n -> BigDecimal.valueOf(n * 10 + 5, 5).setScale(10));
        return Arbitraries.frequencyOf(net.jqwik.api.Tuple.of(3, scale10), net.jqwik.api.Tuple.of(1, halves));
    }

    @Provide
    Arbitrary<BigDecimal> dailyDepth() {
        return Arbitraries.integers().between(0, 100_000).map(i -> BigDecimal.valueOf(i, 2));
    }

    @Provide
    Arbitrary<WaterBalanceInputs> firstDayInputs() {
        return Combinators.combine(
                        dailyDepth(), dailyDepth(), dailyDepth(),
                        Arbitraries.integers().between(1, 200).map(i -> BigDecimal.valueOf(i, 2)),
                        Arbitraries.integers().between(1, 100).map(i -> BigDecimal.valueOf(i, 2)),
                        Arbitraries.integers().between(1, 5_000_000).map(i -> BigDecimal.valueOf(i, 4)))
                .as((etp, p, ir, kc, f, rate) -> new WaterBalanceInputs(
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
                        true,
                        null));
    }
}
