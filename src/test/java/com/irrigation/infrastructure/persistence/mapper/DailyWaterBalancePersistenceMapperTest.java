package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.waterbalance.NoRecommendationReason;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import com.irrigation.infrastructure.persistence.entity.DailyWaterBalanceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de ida e volta do {@link DailyWaterBalancePersistenceMapper} (Req 12.11).
 *
 * <p>Os valores numéricos são comparados com {@code compareTo}, nunca com {@code equals}, porque
 * {@code 1.50} e {@code 1.5} são iguais em valor mas diferentes em escala.
 */
class DailyWaterBalancePersistenceMapperTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-03-10T12:34:56.789Z");

    private final DailyWaterBalancePersistenceMapper mapper = new DailyWaterBalancePersistenceMapper();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static WaterBalanceInputs inputs(boolean firstDay, BigDecimal previousAfdFinal) {
        return new WaterBalanceInputs(
                new EtpDepth(bd("5.25")),
                new PrecipitationDepth(bd("1.50")),
                new AppliedIrrigationDepth(bd("2.75")),
                new GravimetricWaterContent(bd("30.50")),
                new GravimetricWaterContent(bd("15.25")),
                new BulkDensity(bd("1.350")),
                new RootDepth(bd("400.00")),
                new Kc(bd("1.05")),
                new AvailabilityFactor(bd("0.3333")),
                new SprinklerRate(bd("12.5000")),
                firstDay,
                previousAfdFinal);
    }

    private static DailyWaterBalance balance(WaterBalanceInputs inputs, WaterBalanceResult result,
                                             int revision, boolean current) {
        return new DailyWaterBalance(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 3, 10),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new CycleDay(7), inputs, result, CALCULATED_AT,
                "test-methodology", "1.0.0", revision, current);
    }

    private static void assertSameValue(BigDecimal actual, BigDecimal expected) {
        assertThat(actual).isNotNull();
        assertThat(actual.compareTo(expected))
                .as("esperado %s, obtido %s", expected, actual)
                .isZero();
    }

    private static void assertSameResult(WaterBalanceResult actual, WaterBalanceResult expected) {
        assertSameValue(actual.etc(), expected.etc());
        assertSameValue(actual.cad(), expected.cad());
        assertSameValue(actual.afdInitial(), expected.afdInitial());
        assertSameValue(actual.afdFinal(), expected.afdFinal());
        assertSameValue(actual.idealIrrigation(), expected.idealIrrigation());
        assertSameValue(actual.operatingTimeHours(), expected.operatingTimeHours());
        assertThat(actual.recommended()).isEqualTo(expected.recommended());
        assertThat(actual.noRecommendationReason()).isEqualTo(expected.noRecommendationReason());
    }

    private static void assertSameInputs(WaterBalanceInputs actual, WaterBalanceInputs expected) {
        assertSameValue(actual.etp().value(), expected.etp().value());
        assertSameValue(actual.precipitation().value(), expected.precipitation().value());
        assertSameValue(actual.appliedIrrigation().value(), expected.appliedIrrigation().value());
        assertSameValue(actual.fieldCapacity().value(), expected.fieldCapacity().value());
        assertSameValue(actual.permanentWiltingPoint().value(), expected.permanentWiltingPoint().value());
        assertSameValue(actual.bulkDensity().value(), expected.bulkDensity().value());
        assertSameValue(actual.rootDepth().value(), expected.rootDepth().value());
        assertSameValue(actual.kc().value(), expected.kc().value());
        assertSameValue(actual.availabilityFactor().value(), expected.availabilityFactor().value());
        assertSameValue(actual.sprinklerRate().value(), expected.sprinklerRate().value());
        assertThat(actual.firstDayOfPlan()).isEqualTo(expected.firstDayOfPlan());
        if (expected.previousAfdFinal() == null) {
            assertThat(actual.previousAfdFinal()).isNull();
        } else {
            assertSameValue(actual.previousAfdFinal(), expected.previousAfdFinal());
        }
    }

    private void assertRoundTrip(DailyWaterBalance original) {
        DailyWaterBalance restored = mapper.toDomain(mapper.toEntity(original));

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.areaId()).isEqualTo(original.areaId());
        assertThat(restored.date()).isEqualTo(original.date());
        assertThat(restored.cropId()).isEqualTo(original.cropId());
        assertThat(restored.soilProfileId()).isEqualTo(original.soilProfileId());
        assertThat(restored.managementPlanId()).isEqualTo(original.managementPlanId());
        assertThat(restored.cycleDay()).isEqualTo(original.cycleDay());
        assertThat(restored.calculatedAt()).isEqualTo(original.calculatedAt());
        assertThat(restored.methodologyId()).isEqualTo(original.methodologyId());
        assertThat(restored.methodologyVersion()).isEqualTo(original.methodologyVersion());
        assertThat(restored.revision()).isEqualTo(original.revision());
        assertThat(restored.current()).isEqualTo(original.current());
        assertSameInputs(restored.inputs(), original.inputs());
        assertSameResult(restored.result(), original.result());
    }

    @Test
    @DisplayName("ida e volta preserva AFDi e AFDf com escala 13 (CAD × F com F de 4 casas)")
    void roundTripPreservesScale13InAfdValues() {
        // CAD = (30.50 - 15.25) / 100 * 1.350 * 400.00 = 82.35; AFD = CAD × 0.3333
        BigDecimal cad = bd("82.3500000000000");
        BigDecimal afdInitial = bd("27.4472550000000");
        BigDecimal afdFinal = bd("21.1234567890123");
        BigDecimal ideal = afdInitial.subtract(afdFinal);
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.5125000000000"), cad, afdInitial, afdFinal, ideal,
                true, null, bd("1.6900000000"));
        DailyWaterBalance original = balance(inputs(false, afdInitial), result, 1, true);

        DailyWaterBalanceEntity entity = mapper.toEntity(original);

        assertThat(entity.getAfdInitialMm().scale()).isEqualTo(13);
        assertThat(entity.getAfdFinalMm().scale()).isEqualTo(13);
        assertRoundTrip(original);
    }

    @Test
    @DisplayName("ida e volta preserva as 13 casas de uma AFDf sem zeros à direita")
    void roundTripPreservesAllThirteenDecimals() {
        BigDecimal afdInitial = bd("12.3456789012345");
        BigDecimal afdFinal = bd("9.8765432109876");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("4.0000000000001"), bd("50.1234567890123"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), true, null, bd("0.2380952381"));
        DailyWaterBalance restored = mapper.toDomain(mapper.toEntity(
                balance(inputs(false, afdInitial), result, 1, true)));

        assertThat(restored.result().afdInitial().toPlainString()).isEqualTo("12.3456789012345");
        assertThat(restored.result().afdFinal().toPlainString()).isEqualTo("9.8765432109876");
    }

    @Test
    @DisplayName("ida e volta preserva AFDf negativa")
    void roundTripPreservesNegativeAfdFinal() {
        BigDecimal afdInitial = bd("3.0000000000000");
        BigDecimal afdFinal = bd("-4.2500000000000");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("7.2500000000000"), bd("82.3500000000000"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), true, null, bd("0.5800000000"));
        DailyWaterBalance original = balance(inputs(false, afdInitial), result, 1, true);

        assertRoundTrip(original);
        assertThat(mapper.toDomain(mapper.toEntity(original)).result().afdFinal().signum()).isNegative();
    }

    @Test
    @DisplayName("ida e volta preserva AFDf maior que a CAD, sem limitar o valor")
    void roundTripPreservesAfdFinalAboveCad() {
        BigDecimal cad = bd("82.3500000000000");
        BigDecimal afdInitial = bd("27.4472550000000");
        BigDecimal afdFinal = bd("130.0000000000001");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("1.0000000000000"), cad, afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), false, null, bd("0.0000000000"));
        DailyWaterBalance original = balance(inputs(false, afdInitial), result, 1, true);

        assertRoundTrip(original);
        DailyWaterBalance restored = mapper.toDomain(mapper.toEntity(original));
        assertThat(restored.result().afdFinal().compareTo(restored.result().cad())).isPositive();
        assertThat(restored.result().idealIrrigation().signum()).isNegative();
    }

    @Test
    @DisplayName("primeiro dia: motivo FIRST_DAY_OF_FIRST_CYCLE e I são preservados, sem AFDf anterior")
    void firstDayPreservesReasonAndIdealIrrigation() {
        BigDecimal afdInitial = bd("27.4472550000000");
        BigDecimal afdFinal = bd("20.0000000000000");
        BigDecimal ideal = afdInitial.subtract(afdFinal);
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.5125000000000"), bd("82.3500000000000"), afdInitial, afdFinal, ideal,
                false, NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE, bd("0.0000000000"));
        DailyWaterBalance original = balance(inputs(true, null), result, 1, true);

        DailyWaterBalanceEntity entity = mapper.toEntity(original);
        assertThat(entity.getNoRecommendationReason()).isEqualTo("FIRST_DAY_OF_FIRST_CYCLE");

        DailyWaterBalance restored = mapper.toDomain(entity);
        assertThat(restored.inputs().firstDayOfPlan()).isTrue();
        assertThat(restored.inputs().previousAfdFinal()).isNull();
        assertThat(restored.result().noRecommendationReason())
                .isEqualTo(NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE);
        assertThat(restored.result().recommended()).isFalse();
        assertSameValue(restored.result().idealIrrigation(), ideal);
        assertRoundTrip(original);
    }

    @Test
    @DisplayName("primeiro dia com I negativa preserva o sinal e o motivo")
    void firstDayWithNegativeIdealIrrigationIsPreserved() {
        BigDecimal afdInitial = bd("10.0000000000000");
        BigDecimal afdFinal = bd("15.5000000000000");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("0.0000000000000"), bd("82.3500000000000"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), false,
                NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE, bd("0.0000000000"));
        DailyWaterBalance original = balance(inputs(true, null), result, 1, true);

        assertRoundTrip(original);
        assertThat(mapper.toDomain(mapper.toEntity(original)).result().idealIrrigation().signum()).isNegative();
    }

    @Test
    @DisplayName("dia seguinte: AFDf anterior é reconstruída como a AFDi e o motivo fica nulo")
    void nextDayRebuildsPreviousAfdFinalFromAfdInitial() {
        BigDecimal afdInitial = bd("20.0000000000000");
        BigDecimal afdFinal = bd("14.1234567890123");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.5125000000000"), bd("82.3500000000000"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), true, null, bd("0.4700000000"));
        DailyWaterBalance original = balance(inputs(false, afdInitial), result, 1, true);

        DailyWaterBalance restored = mapper.toDomain(mapper.toEntity(original));

        assertThat(restored.inputs().firstDayOfPlan()).isFalse();
        assertThat(restored.inputs().previousAfdFinal()).isNotNull();
        assertSameValue(restored.inputs().previousAfdFinal(), afdInitial);
        assertThat(restored.result().noRecommendationReason()).isNull();
        assertRoundTrip(original);
    }

    @Test
    @DisplayName("tempo de funcionamento mantém a escala 10")
    void operatingTimePreservesScale10() {
        BigDecimal operatingTime = bd("1.2345678901");
        BigDecimal afdInitial = bd("20.0000000000000");
        BigDecimal afdFinal = bd("14.0000000000000");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.0000000000000"), bd("82.3500000000000"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), true, null, operatingTime);
        DailyWaterBalance original = balance(inputs(false, afdInitial), result, 1, true);

        DailyWaterBalanceEntity entity = mapper.toEntity(original);

        assertThat(entity.getOperatingTimeHours().scale()).isEqualTo(10);
        assertSameValue(entity.getOperatingTimeHours(), operatingTime);
        DailyWaterBalance restored = mapper.toDomain(entity);
        assertThat(restored.result().operatingTimeHours().scale()).isEqualTo(10);
        assertSameValue(restored.result().operatingTimeHours(), operatingTime);
    }

    @Test
    @DisplayName("revision e current são preservados, inclusive em revisão antiga não corrente")
    void revisionAndCurrentArePreserved() {
        BigDecimal afdInitial = bd("20.0000000000000");
        BigDecimal afdFinal = bd("14.0000000000000");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.0000000000000"), bd("82.3500000000000"), afdInitial, afdFinal,
                afdInitial.subtract(afdFinal), true, null, bd("0.4800000000"));
        DailyWaterBalance current = balance(inputs(false, afdInitial), result, 3, true);
        DailyWaterBalance old = current.markNotCurrent();

        DailyWaterBalance restoredCurrent = mapper.toDomain(mapper.toEntity(current));
        DailyWaterBalance restoredOld = mapper.toDomain(mapper.toEntity(old));

        assertThat(restoredCurrent.revision()).isEqualTo(3);
        assertThat(restoredCurrent.current()).isTrue();
        assertThat(restoredOld.revision()).isEqualTo(3);
        assertThat(restoredOld.current()).isFalse();
    }

    @Test
    @DisplayName("instante do cálculo volta igual, convertido por UTC")
    void calculatedAtIsStoredInUtc() {
        BigDecimal afdInitial = bd("20.0000000000000");
        WaterBalanceResult result = new WaterBalanceResult(
                bd("5.0000000000000"), bd("82.3500000000000"), afdInitial, bd("14.0000000000000"),
                bd("6.0000000000000"), true, null, bd("0.4800000000"));
        DailyWaterBalanceEntity entity = mapper.toEntity(
                balance(inputs(false, afdInitial), result, 1, true));

        assertThat(entity.getCalculatedAt())
                .isEqualTo(LocalDateTime.ofInstant(CALCULATED_AT, ZoneOffset.UTC));
        assertThat(mapper.toDomain(entity).calculatedAt()).isEqualTo(CALCULATED_AT);
    }
}
