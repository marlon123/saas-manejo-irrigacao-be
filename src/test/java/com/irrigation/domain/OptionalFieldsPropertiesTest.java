package com.irrigation.domain;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedade 12 do módulo de balanço hídrico: pares de umidade e campos opcionais
 * (cadastros de solo, cultura e área). Roda sem Docker.
 *
 * <p>Valida: Requisitos 1.4, 1.5, 1.6, 1.7, 1.8, 2.4, 2.5, 2.11, 3.1
 */
@PropertyDefaults(tries = 200)
class OptionalFieldsPropertiesTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Resultado da tentativa de construir um agregado: aceito ou recusado com IllegalArgumentException. */
    private static boolean accepted(Supplier<?> builder) {
        try {
            builder.get();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static <T> T maybe(boolean present, BigDecimal value, Function<BigDecimal, T> factory) {
        return present ? factory.apply(value) : null;
    }

    private static boolean inRange(BigDecimal v, String min, String max) {
        return v.compareTo(bd(min)) >= 0 && v.compareTo(bd(max)) <= 0;
    }

    // =====================================================================================
    // Solo: pares volumétrico e gravimétrico, unidades e profundidade efetiva
    // =====================================================================================

    /**
     * Combinação de presença (quatro membros dos pares, unidades, profundidade e água disponível) e de valores,
     * inclusive fora da faixa.
     */
    record SoilCase(
            boolean hasFc, BigDecimal fc, boolean hasPwp, BigDecimal pwp,
            boolean hasFcG, BigDecimal fcG, boolean hasPwpG, BigDecimal pwpG,
            boolean hasUnits, boolean hasDepth, BigDecimal depth, boolean hasAvailable, BigDecimal available) {
    }

    @Provide
    Arbitrary<SoilCase> soilCases() {
        Arbitrary<BigDecimal> volumetric = Arbitraries.bigDecimals().between(bd("-0.10"), bd("1.10")).ofScale(2);
        Arbitrary<BigDecimal> gravimetric = Arbitraries.bigDecimals().between(bd("-5"), bd("105")).ofScale(2);
        Arbitrary<BigDecimal> depth = Arbitraries.bigDecimals().between(bd("-1"), bd("5100")).ofScale(2);
        Arbitrary<BigDecimal> available = Arbitraries.bigDecimals().between(bd("-1"), bd("500")).ofScale(2);
        Arbitrary<Boolean> flag = Arbitraries.of(true, false);
        return Combinators.combine(flag, volumetric, flag, volumetric, flag, gravimetric, flag, gravimetric)
                .as(PairPart::new)
                .flatMap(p -> Combinators.combine(flag, flag, depth, flag, available)
                        .as((units, hasDepth, d, hasAvailable, a) -> new SoilCase(
                                p.hasFc, p.fc, p.hasPwp, p.pwp, p.hasFcG, p.fcG, p.hasPwpG, p.pwpG,
                                units, hasDepth, d, hasAvailable, a)));
    }

    private record PairPart(boolean hasFc, BigDecimal fc, boolean hasPwp, BigDecimal pwp,
                            boolean hasFcG, BigDecimal fcG, boolean hasPwpG, BigDecimal pwpG) {
    }

    private static boolean pairOk(boolean hasFirst, BigDecimal first, boolean hasSecond, BigDecimal second,
                                  String min, String max) {
        if (hasFirst != hasSecond) {
            return false;
        }
        if (!hasFirst) {
            return true;
        }
        return inRange(first, min, max) && inRange(second, min, max) && first.compareTo(second) > 0;
    }

    // Feature: irrigation-water-balance-module, Property 12: Pares de umidade e campos opcionais
    // Valida: Requisitos 1.4, 1.5, 1.6, 1.7, 1.8
    @Property
    void soilIsAcceptedExactlyWhenEachMoisturePairIsCompleteOrAbsentAndValid(@ForAll("soilCases") SoilCase c) {
        boolean expected = pairOk(c.hasFc, c.fc, c.hasPwp, c.pwp, "0", "1")
                && pairOk(c.hasFcG, c.fcG, c.hasPwpG, c.pwpG, "0", "100")
                && (!c.hasDepth || (c.depth.signum() > 0 && c.depth.compareTo(bd("5000")) <= 0))
                && (!c.hasAvailable || c.available.signum() >= 0);

        MeasurementUnit unit = c.hasUnits ? MeasurementUnit.MILLIMETERS : null;
        boolean built = accepted(() -> new SoilProfile(UUID.randomUUID(), "Franco",
                maybe(c.hasFc, c.fc, VolumetricWaterContent::new),
                maybe(c.hasPwp, c.pwp, VolumetricWaterContent::new),
                maybe(c.hasFcG, c.fcG, GravimetricWaterContent::new),
                maybe(c.hasPwpG, c.pwpG, GravimetricWaterContent::new),
                new BulkDensity(bd("1.4")),
                maybe(c.hasDepth, c.depth, RootDepth::new),
                maybe(c.hasAvailable, c.available, AvailableWater::new),
                unit, unit, unit, unit));

        assertThat(built).as("caso %s", c).isEqualTo(expected);
    }

    // Feature: irrigation-water-balance-module, Property 12: Pares de umidade e campos opcionais
    // Valida: Requisitos 1.6, 1.8 (um solo aceito preserva a ausência dos opcionais, sem assumir padrões)
    @Property
    void soilWithOnlyMandatoryFieldsKeepsOptionalFieldsAbsent(
            @ForAll @IntRange(min = 0, max = 3) int presentPair) {
        // presentPair: 0 = nenhum par, 1 = só volumétrico, 2 = só gravimétrico, 3 = ambos
        boolean volumetric = presentPair == 1 || presentPair == 3;
        boolean gravimetric = presentPair == 2 || presentPair == 3;
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Argiloso",
                maybe(volumetric, bd("0.35"), VolumetricWaterContent::new),
                maybe(volumetric, bd("0.15"), VolumetricWaterContent::new),
                maybe(gravimetric, bd("30"), GravimetricWaterContent::new),
                maybe(gravimetric, bd("12"), GravimetricWaterContent::new),
                new BulkDensity(bd("1.4")), null, null, null, null, null, null);

        assertThat(soil.getFieldCapacity() != null).isEqualTo(volumetric);
        assertThat(soil.getPermanentWiltingPoint() != null).isEqualTo(volumetric);
        assertThat(soil.getFieldCapacityGravimetric() != null).isEqualTo(gravimetric);
        assertThat(soil.getPermanentWiltingPointGravimetric() != null).isEqualTo(gravimetric);
        assertThat(soil.getEffectiveRootDepth()).isNull();
        assertThat(soil.getAvailableWater()).isNull();
        assertThat(soil.getSoilWaterUnit()).isNull();
        assertThat(soil.getBulkDensityUnit()).isNull();
        assertThat(soil.getRootDepthUnit()).isNull();
        assertThat(soil.getAvailableWaterUnit()).isNull();
    }

    // =====================================================================================
    // Cultura: de 0 a 20 estágios e KC e Z opcionais
    // =====================================================================================

    // Feature: irrigation-water-balance-module, Property 12: Pares de umidade e campos opcionais
    // Valida: Requisitos 2.4, 2.5, 2.11
    @Property
    void cropAcceptsZeroToTwentyStagesAndRejectsMoreRegardlessOfOptionalKcAndRootDepth(
            @ForAll @IntRange(min = 0, max = 30) int stageCount,
            @ForAll boolean hasKc, @ForAll boolean hasRootDepth) {
        List<CropStage> stages = IntStream.rangeClosed(1, stageCount)
                .mapToObj(i -> new CropStage("E" + i, new Kc(BigDecimal.ONE), i)).toList();

        boolean built = accepted(() -> new Crop(UUID.randomUUID(), "Milho", null, null, stages,
                hasKc ? new Kc(bd("1.15")) : null,
                hasRootDepth ? new RootDepth(bd("600")) : null));

        assertThat(built).as("%d estágios", stageCount).isEqualTo(stageCount <= 20);
    }

    // =====================================================================================
    // Área: só o nome é obrigatório
    // =====================================================================================

    record AreaCase(String name, boolean hasArea, BigDecimal area, boolean hasType,
                    boolean hasFlow, BigDecimal flow) {
    }

    @Provide
    Arbitrary<AreaCase> areaCases() {
        Arbitrary<String> names = Arbitraries.oneOf(
                Arbitraries.integers().between(0, 300).map("a"::repeat),
                Arbitraries.integers().between(0, 5).map(" "::repeat));
        Arbitrary<BigDecimal> area = Arbitraries.bigDecimals().between(bd("-1"), bd("1000001")).ofScale(2);
        Arbitrary<BigDecimal> flow = Arbitraries.bigDecimals().between(bd("-1"), bd("1000001")).ofScale(2);
        Arbitrary<Boolean> flag = Arbitraries.of(true, false);
        return Combinators.combine(names, flag, area, flag, flag, flow).as(AreaCase::new);
    }

    // Feature: irrigation-water-balance-module, Property 12: Pares de umidade e campos opcionais
    // Valida: Requisitos 3.1
    @Property
    void areaIsAcceptedExactlyWhenNameIsValidAndPresentOptionalFieldsAreValid(@ForAll("areaCases") AreaCase c) {
        boolean expected = !c.name.isBlank() && c.name.length() <= 255
                && (!c.hasArea || (c.area.signum() > 0 && c.area.compareTo(bd("1000000")) <= 0))
                && (!c.hasFlow || (c.flow.signum() > 0 && c.flow.compareTo(bd("1000000")) <= 0));

        boolean built = accepted(() -> new Area(UUID.randomUUID(), c.name,
                maybe(c.hasArea, c.area, AreaInHectares::new), null, null,
                c.hasType ? IrrigationSystemType.values()[0] : null,
                maybe(c.hasFlow, c.flow, FlowRate::new), null));

        assertThat(built).as("caso %s", c).isEqualTo(expected);
    }
}
