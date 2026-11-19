package com.irrigation.domain.waterbalance;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 5: Regra de recomendação.
 *
 * <p>Para qualquer entrada válida, o dia é recomendado se e somente se {@code I > 0} e o dia não é o
 * primeiro do plano. O motivo {@code FIRST_DAY_OF_FIRST_CYCLE} existe se e somente se o dia é o
 * primeiro do plano, qualquer que seja o sinal de I. Dia não recomendado tem tempo de funcionamento
 * zero. I = AFDi − AFDf é preservado com seu sinal, e IR (campo da entrada) nunca é alterado nem
 * substituído por I.
 *
 * <p><b>Validates: Requirements 9.2, 9.3, 9.4, 9.5, 9.6, 10.2, 6.8</b>
 */
class RecommendationRulePropertyTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    @Provide
    Arbitrary<WaterBalanceInputs> anyDay() {
        return Arbitraries.of(true, false).flatMap(WaterBalanceArbitraries::inputs);
    }

    @Provide
    Arbitrary<WaterBalanceInputs> firstDay() {
        return WaterBalanceArbitraries.inputs(true);
    }

    @Provide
    Arbitrary<WaterBalanceInputs> followingDay() {
        return WaterBalanceArbitraries.inputs(false);
    }

    /** Dia seguinte com KC = 1, P = 0 e IR = ETP: ETc = IR e, portanto, I = 0 exatamente. */
    @Provide
    Arbitrary<WaterBalanceInputs> followingDayWithZeroIdealIrrigation() {
        return Arbitraries.integers().between(0, 100_000).flatMap(cents ->
                WaterBalanceArbitraries.inputs(false).map(in -> new WaterBalanceInputs(
                        new com.irrigation.domain.model.valueobjects.EtpDepth(BigDecimal.valueOf(cents, 2)),
                        new com.irrigation.domain.model.valueobjects.PrecipitationDepth(BigDecimal.ZERO),
                        new AppliedIrrigationDepth(BigDecimal.valueOf(cents, 2)),
                        in.fieldCapacity(), in.permanentWiltingPoint(), in.bulkDensity(), in.rootDepth(),
                        new com.irrigation.domain.model.valueobjects.Kc(BigDecimal.ONE),
                        in.availabilityFactor(), in.sprinklerRate(), false, in.previousAfdFinal())));
    }

    // Feature: irrigation-water-balance-module, Property 5: Regra de recomendação
    @Property(tries = 300)
    void recommendedIffPositiveIdealIrrigationAndNotFirstDay(@ForAll("anyDay") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);

        assertThat(r.idealIrrigation().compareTo(r.afdInitial().subtract(r.afdFinal()))).isZero();
        assertThat(r.recommended())
                .isEqualTo(r.idealIrrigation().signum() > 0 && !in.firstDayOfPlan());

        assertThat(r.noRecommendationReason() == NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE)
                .isEqualTo(in.firstDayOfPlan());

        if (!r.recommended()) {
            assertThat(r.operatingTimeHours().compareTo(BigDecimal.ZERO)).isZero();
        }
    }

    // Feature: irrigation-water-balance-module, Property 5: Regra de recomendação
    @Property(tries = 300)
    void firstDayIsNeverRecommendedAndKeepsSignedIdealIrrigation(@ForAll("firstDay") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);

        assertThat(r.recommended()).isFalse();
        assertThat(r.operatingTimeHours().compareTo(BigDecimal.ZERO)).isZero();
        assertThat(r.noRecommendationReason()).isEqualTo(NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE);
        // I é preservado com o sinal, seja positivo, zero ou negativo
        assertThat(r.idealIrrigation().compareTo(r.afdInitial().subtract(r.afdFinal()))).isZero();
        BigDecimal etc = in.kc().value().multiply(in.etp().value());
        BigDecimal expected = etc.subtract(in.precipitation().value()).subtract(in.appliedIrrigation().value());
        assertThat(r.idealIrrigation().compareTo(expected)).isZero();
    }

    // Feature: irrigation-water-balance-module, Property 5: Regra de recomendação
    @Property(tries = 300)
    void followingDayWithNonPositiveIdealIrrigationHasNoReasonAndZeroTime(
            @ForAll("followingDay") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);

        if (r.idealIrrigation().signum() <= 0) {
            assertThat(r.recommended()).isFalse();
            assertThat(r.noRecommendationReason()).isNull();
            assertThat(r.operatingTimeHours().compareTo(BigDecimal.ZERO)).isZero();
        } else {
            assertThat(r.recommended()).isTrue();
            assertThat(r.noRecommendationReason()).isNull();
            assertThat(r.operatingTimeHours().signum()).isGreaterThanOrEqualTo(0);
        }
    }

    // Feature: irrigation-water-balance-module, Property 5: Regra de recomendação
    @Property(tries = 100)
    void zeroIdealIrrigationIsNotRecommended(@ForAll("followingDayWithZeroIdealIrrigation") WaterBalanceInputs in) {
        WaterBalanceResult r = calculator.calculate(in);

        assertThat(r.idealIrrigation().signum()).isZero();
        assertThat(r.recommended()).isFalse();
        assertThat(r.noRecommendationReason()).isNull();
        assertThat(r.operatingTimeHours().compareTo(BigDecimal.ZERO)).isZero();
    }

    // Feature: irrigation-water-balance-module, Property 5: Regra de recomendação
    @Property(tries = 200)
    void irAndIdealIrrigationAreDistinctQuantities(
            @ForAll("anyDay") WaterBalanceInputs in,
            @ForAll("irValues") BigDecimal otherIr) {

        BigDecimal originalIr = in.appliedIrrigation().value();
        WaterBalanceInputs changed = new WaterBalanceInputs(
                in.etp(), in.precipitation(), new AppliedIrrigationDepth(otherIr),
                in.fieldCapacity(), in.permanentWiltingPoint(), in.bulkDensity(), in.rootDepth(),
                in.kc(), in.availabilityFactor(), in.sprinklerRate(),
                in.firstDayOfPlan(), in.previousAfdFinal());

        WaterBalanceResult before = calculator.calculate(in);
        WaterBalanceResult after = calculator.calculate(changed);

        // o cálculo não sobrescreve IR nas entradas
        assertThat(in.appliedIrrigation().value()).isEqualByComparingTo(originalIr);
        assertThat(changed.appliedIrrigation().value()).isEqualByComparingTo(otherIr);

        // alterar IR muda I exatamente em −ΔIR, e I não é igual a IR por substituição
        BigDecimal delta = otherIr.subtract(originalIr);
        assertThat(after.idealIrrigation().compareTo(before.idealIrrigation().subtract(delta))).isZero();
    }

    @Provide
    Arbitrary<BigDecimal> irValues() {
        return WaterBalanceArbitraries.depthMm();
    }
}
