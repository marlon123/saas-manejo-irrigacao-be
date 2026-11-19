package com.irrigation.domain.waterbalance;

import com.irrigation.application.waterbalance.PresentationRounding;
import com.irrigation.application.waterbalance.PresentationRounding.OperatingTimeView;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos de borda do calculador e do arredondamento de apresentação.
 * Complementa o {@link WaterBalanceScenariosTest}, sem repetir os cenários A, B, C e E.
 *
 * (Req 9.3, 9.6, 10.3, 15.4, 15.6, 15.10)
 */
class WaterBalanceEdgeCasesTest {

    private final WaterBalanceCalculator calculator = new WaterBalanceCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** CAD = 72 mm; AFDi do primeiro dia com F = 0,50 é 36 mm. */
    private static WaterBalanceInputs inputs(String etp, String p, String ir, String rate,
                                             boolean first, BigDecimal previousAfdFinal) {
        return new WaterBalanceInputs(
                new EtpDepth(bd(etp)),
                new PrecipitationDepth(bd(p)),
                new AppliedIrrigationDepth(bd(ir)),
                new GravimetricWaterContent(bd("30")),
                new GravimetricWaterContent(bd("15")),
                new BulkDensity(bd("1.2")),
                new RootDepth(bd("400")),
                new Kc(bd("0.8")),
                new AvailabilityFactor(bd("0.50")),
                new SprinklerRate(bd(rate)),
                first,
                previousAfdFinal);
    }

    // ---------- WaterBalanceInputs ----------

    @Test
    @DisplayName("Primeiro dia com AFDf anterior presente é rejeitado")
    void firstDayWithPreviousAfdFinalIsRejected() {
        assertThatThrownBy(() -> inputs("5", "0", "0", "6", true, bd("30")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primeiro dia");
    }

    @Test
    @DisplayName("Dia seguinte sem AFDf anterior é rejeitado")
    void followingDayWithoutPreviousAfdFinalIsRejected() {
        assertThatThrownBy(() -> inputs("5", "0", "0", "6", false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatória");
    }

    @ParameterizedTest(name = "Value object nulo na posição {0} lança NullPointerException")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void nullValueObjectIsRejected(int nullIndex) {
        Object[] a = {
                new EtpDepth(bd("5")), new PrecipitationDepth(bd("0")),
                new AppliedIrrigationDepth(bd("0")), new GravimetricWaterContent(bd("30")),
                new GravimetricWaterContent(bd("15")), new BulkDensity(bd("1.2")),
                new RootDepth(bd("400")), new Kc(bd("0.8")),
                new AvailabilityFactor(bd("0.50")), new SprinklerRate(bd("6"))};
        a[nullIndex] = null;

        assertThatThrownBy(() -> new WaterBalanceInputs(
                (EtpDepth) a[0], (PrecipitationDepth) a[1], (AppliedIrrigationDepth) a[2],
                (GravimetricWaterContent) a[3], (GravimetricWaterContent) a[4],
                (BulkDensity) a[5], (RootDepth) a[6], (Kc) a[7],
                (AvailabilityFactor) a[8], (SprinklerRate) a[9], true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Entradas nulas no calculador lançam NullPointerException")
    void nullInputsToCalculatorAreRejected() {
        assertThatThrownBy(() -> calculator.calculate(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- Divisor zero ----------

    @Test
    @DisplayName("SprinklerRate zero não chega ao calculador: o value object o rejeita")
    void zeroSprinklerRateIsRejectedByValueObject() {
        assertThatThrownBy(() -> new SprinklerRate(BigDecimal.ZERO))
                .isInstanceOf(RuntimeException.class);
    }

    /**
     * O divisor zero é inalcançável pela API pública, porque {@link SprinklerRate} rejeita zero.
     * A guarda defensiva do método privado {@code divide} é exercitada por reflexão.
     */
    @Test
    @DisplayName("Guarda defensiva de divisor zero lança 'divisor inválido'")
    void zeroDivisorGuardThrowsInvalidDivisor() throws Exception {
        Method divide = WaterBalanceCalculator.class
                .getDeclaredMethod("divide", BigDecimal.class, BigDecimal.class);
        divide.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                divide.invoke(null, bd("5"), BigDecimal.ZERO);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("divisor inválido");
    }

    // ---------- Recomendação ----------

    @Test
    @DisplayName("I exatamente zero: não recomendado, tempo zero e sem motivo")
    void exactlyZeroIdealIrrigation() {
        WaterBalanceResult r = calculator.calculate(inputs("0", "0", "0", "6", false, bd("30")));

        assertThat(r.idealIrrigation()).isEqualByComparingTo("0");
        assertThat(r.recommended()).isFalse();
        assertThat(r.operatingTimeHours()).isEqualByComparingTo("0");
        assertThat(r.noRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("Segundo dia com I > 0: recomendado e sem motivo")
    void secondDayWithPositiveIdealIsRecommended() {
        WaterBalanceResult r = calculator.calculate(inputs("5", "0", "0", "6", false, bd("30")));

        assertThat(r.idealIrrigation()).isEqualByComparingTo("4");
        assertThat(r.recommended()).isTrue();
        assertThat(r.operatingTimeHours()).isGreaterThan(BigDecimal.ZERO);
        assertThat(r.noRecommendationReason()).isNull();
    }

    @Test
    @DisplayName("Primeiro dia com I > 0: não recomendado, com motivo, I preservado e tempo zero")
    void firstDayWithPositiveIdealIsNotRecommended() {
        WaterBalanceResult r = calculator.calculate(inputs("5", "0", "0", "6", true, null));

        assertThat(r.idealIrrigation()).isEqualByComparingTo("4");
        assertThat(r.recommended()).isFalse();
        assertThat(r.operatingTimeHours()).isEqualByComparingTo("0");
        assertThat(r.noRecommendationReason())
                .isEqualTo(NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE);
    }

    // ---------- PresentationRounding ----------

    @Test
    @DisplayName("59,6 minutos viram 1 h 00 min (carry dos minutos)")
    void minutesCarryToNextHour() {
        // 0,9933333333 h x 60 = 59,59999999980 min, que arredonda para 60
        OperatingTimeView view = PresentationRounding.operatingTime(bd("0.9933333333"));

        assertThat(view.wholeHours()).isEqualTo(1);
        assertThat(view.minutes()).isZero();
        assertThat(view.hours()).isEqualByComparingTo("0.9933");
    }

    @Test
    @DisplayName("59,4 minutos permanecem em 0 h 59 min")
    void minutesBelowCarryStay() {
        OperatingTimeView view = PresentationRounding.operatingTime(bd("0.9900000000"));

        assertThat(view.wholeHours()).isZero();
        assertThat(view.minutes()).isEqualTo(59);
    }

    @ParameterizedTest(name = "roundMm({0}) = {1}")
    @CsvSource({
            "-0.005, -0.01",
            "-0.004, 0.00",
            "-1.235, -1.24",
            "0.005,  0.01",
            "1.234,  1.23"
    })
    void roundMmUsesHalfUpIncludingNegatives(String value, String expected) {
        assertThat(PresentationRounding.roundMm(bd(value))).isEqualByComparingTo(expected);
        assertThat(PresentationRounding.roundMm(bd(value)).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundHours usa 4 casas e HALF_UP")
    void roundHoursUsesHalfUp() {
        assertThat(PresentationRounding.roundHours(bd("0.12345"))).isEqualByComparingTo("0.1235");
        assertThat(PresentationRounding.roundHours(bd("0.12345")).scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("Horas negativas em operatingTime lançam IllegalArgumentException")
    void negativeHoursAreRejected() {
        assertThatThrownBy(() -> PresentationRounding.operatingTime(bd("-0.0000000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativas");
    }

    @Test
    @DisplayName("Valores nulos em PresentationRounding lançam NullPointerException")
    void nullsAreRejected() {
        assertThatThrownBy(() -> PresentationRounding.roundMm(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PresentationRounding.roundHours(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PresentationRounding.operatingTime(null))
                .isInstanceOf(NullPointerException.class);
    }
}
