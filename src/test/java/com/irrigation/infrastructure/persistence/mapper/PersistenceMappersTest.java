package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.infrastructure.persistence.entity.AreaEntity;
import com.irrigation.infrastructure.persistence.entity.CropEntity;
import com.irrigation.infrastructure.persistence.entity.CropStageEntity;
import com.irrigation.infrastructure.persistence.entity.RecommendationEntity;
import com.irrigation.infrastructure.persistence.entity.SoilProfileEntity;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapeadores manuais de persistência (decisão 2 do handoff): domínio → entidade → domínio
 * preserva todos os campos, inclusive os opcionais. Testes puros, sem banco; o caminho
 * pelo PostgreSQL real é coberto por RepositoryRoundTripIntegrationTest.
 */
class PersistenceMappersTest {

    private final AreaPersistenceMapper areaMapper = new AreaPersistenceMapper();
    private final CropPersistenceMapper cropMapper = new CropPersistenceMapper();
    private final SoilProfilePersistenceMapper soilMapper = new SoilProfilePersistenceMapper();
    private final RecommendationPersistenceMapper recommendationMapper = new RecommendationPersistenceMapper();

    private static <T> void assertSameAggregate(T actual, T expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    // =====================================================================================
    // Área
    // =====================================================================================

    @Provide
    Arbitrary<Area> areas() {
        Arbitrary<GeographicLocation> location = Arbitraries.of(
                new GeographicLocation(bd("-15.793889"), bd("-47.882778")),
                new GeographicLocation(bd("90"), bd("-180")),
                new GeographicLocation(bd("0"), bd("0"))).injectNull(0.4);
        Arbitrary<IrrigationEfficiency> efficiency = Arbitraries.bigDecimals()
                .between(bd("0.01"), bd("1")).ofScale(2).map(IrrigationEfficiency::new).injectNull(0.4);
        Arbitrary<String> characteristics = Arbitraries.strings().alpha().ofMaxLength(40).injectNull(0.4);
        Arbitrary<AreaInHectares> hectares = Arbitraries.bigDecimals()
                .between(bd("0.01"), bd("1000000.00")).ofScale(2).map(AreaInHectares::new);

        return Combinators.combine(location, efficiency, characteristics, hectares,
                        Arbitraries.of(IrrigationSystemType.class))
                .as((loc, eff, chars, ha, type) -> new Area(UUID.randomUUID(), "Área", ha, loc, chars, type,
                        new FlowRate(bd("120.50")), eff));
    }

    // Feature: irrigation-management-backend — round-trip do mapeador de área com opcionais
    @Property(tries = 200)
    void areaRoundTripsWithAnyCombinationOfOptionalFields(@ForAll("areas") Area area) {
        assertSameAggregate(areaMapper.toDomain(areaMapper.toEntity(area)), area);
    }

    @Test
    void areaWithoutLocationOrEfficiencyLeavesTheColumnsNull() {
        Area area = new Area(UUID.randomUUID(), "Sulcos", new AreaInHectares(bd("3")), null, null,
                IrrigationSystemType.FURROW, new FlowRate(bd("15")), null);

        AreaEntity entity = areaMapper.toEntity(area);

        assertThat(entity.getId()).isEqualTo(area.getId());
        assertThat(entity.getLatitude()).isNull();
        assertThat(entity.getLongitude()).isNull();
        assertThat(entity.getIrrigationEfficiency()).isNull();
        assertThat(entity.getGeneralCharacteristics()).isNull();
        assertThat(entity.getIrrigationSystemType()).isEqualTo(IrrigationSystemType.FURROW);
    }

    /** Coordenada incompleta no banco não vira uma localização inválida no domínio. */
    @Test
    void areaWithOnlyOneCoordinateMapsToNoLocation() {
        AreaEntity entity = areaMapper.toEntity(TestData.area());
        entity.setLatitude(bd("-10"));
        entity.setLongitude(null);

        assertThat(areaMapper.toDomain(entity).getGeographicLocation()).isNull();
    }

    // =====================================================================================
    // Cultura
    // =====================================================================================

    @Test
    void cropRoundTripsKeepingStagesAndOrder() {
        Crop crop = TestData.crop();

        assertSameAggregate(cropMapper.toDomain(cropMapper.toEntity(crop)), crop);
    }

    @Test
    void cropWithoutOptionalFieldsRoundTrips() {
        Crop crop = new Crop(UUID.randomUUID(), "Feijão", null, null,
                List.of(new CropStage("Único", new Kc(bd("1.05")), 1)));

        assertSameAggregate(cropMapper.toDomain(cropMapper.toEntity(crop)), crop);
    }

    @Test
    void eachPersistedStageGetsANewIdentity() {
        Crop crop = TestData.crop();

        CropEntity first = cropMapper.toEntity(crop);
        CropEntity second = cropMapper.toEntity(crop);

        assertThat(first.getStages()).extracting(CropStageEntity::getId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(first.getStages()).extracting(CropStageEntity::getStageOrder).containsExactly(1, 2);
        assertThat(first.getStages()).extracting(CropStageEntity::getId)
                .doesNotContainAnyElementsOf(second.getStages().stream().map(CropStageEntity::getId).toList());
    }

    // =====================================================================================
    // Perfil de solo
    // =====================================================================================

    @Test
    void soilProfileRoundTripsIncludingUnits() {
        SoilProfile soil = TestData.soilProfile();

        SoilProfileEntity entity = soilMapper.toEntity(soil);

        assertThat(entity.getSoilWaterUnit()).isEqualTo(soil.getSoilWaterUnit());
        assertThat(entity.getAvailableWaterUnit()).isEqualTo(soil.getAvailableWaterUnit());
        assertSameAggregate(soilMapper.toDomain(entity), soil);
    }

    // =====================================================================================
    // Módulo de balanço hídrico: campos nulos e campos novos (Req 1.10, 1.11, 2.7, 3.4)
    // =====================================================================================

    @Test
    void areaWithOnlyTheNameMapsEveryOptionalColumnToNull() {
        Area area = new Area(UUID.randomUUID(), "Só o nome", null, null, null, null, null, null);

        AreaEntity entity = areaMapper.toEntity(area);

        assertThat(entity.getAreaHectares()).isNull();
        assertThat(entity.getIrrigationSystemType()).isNull();
        assertThat(entity.getSystemFlowRate()).isNull();
        assertSameAggregate(areaMapper.toDomain(entity), area);
    }

    @Test
    void areaEntityWithNullColumnsMapsToADomainAreaWithNulls() {
        AreaEntity entity = areaMapper.toEntity(TestData.area());
        entity.setAreaHectares(null);
        entity.setIrrigationSystemType(null);
        entity.setSystemFlowRate(null);

        Area area = areaMapper.toDomain(entity);

        assertThat(area.getAreaHectares()).isNull();
        assertThat(area.getIrrigationSystemType()).isNull();
        assertThat(area.getSystemFlowRate()).isNull();
        assertThat(area.getName()).isEqualTo("Talhão 1");
    }

    @Test
    void cropWithKcAndRootDepthRoundTripsAndFillsTheNewColumns() {
        Crop crop = new Crop(UUID.randomUUID(), "Milho", "Híbrido", 120,
                List.of(new CropStage("Inicial", new Kc(bd("0.40")), 1)), new Kc(bd("1.15")), new RootDepth(bd("450.50")));

        CropEntity entity = cropMapper.toEntity(crop);

        assertThat(entity.getKc()).isEqualByComparingTo("1.15");
        assertThat(entity.getRootDepthMm()).isEqualByComparingTo("450.50");
        assertSameAggregate(cropMapper.toDomain(entity), crop);
    }

    @Test
    void cropWithZeroStagesRoundTrips() {
        Crop crop = new Crop(UUID.randomUUID(), "Sem estágios", null, null, List.of(),
                new Kc(bd("1.00")), new RootDepth(bd("400")));

        CropEntity entity = cropMapper.toEntity(crop);

        assertThat(entity.getStages()).isEmpty();
        Crop restored = cropMapper.toDomain(entity);
        assertThat(restored.getStages()).isEmpty();
        assertSameAggregate(restored, crop);
    }

    @Test
    void legacyCropEntityWithoutKcAndRootDepthMapsToNulls() {
        CropEntity entity = cropMapper.toEntity(TestData.crop());

        assertThat(entity.getKc()).isNull();
        assertThat(entity.getRootDepthMm()).isNull();
        Crop crop = cropMapper.toDomain(entity);
        assertThat(crop.getKc()).isNull();
        assertThat(crop.getRootDepth()).isNull();
    }

    @Test
    void soilProfileWithGravimetricPairRoundTripsAndFillsTheNewColumns() {
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Argiloso",
                new VolumetricWaterContent(bd("0.35")), new VolumetricWaterContent(bd("0.18")),
                new GravimetricWaterContent(bd("28.50")), new GravimetricWaterContent(bd("14.20")),
                new BulkDensity(bd("1.30")), new RootDepth(bd("600.00")), new AvailableWater(bd("100.00")),
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);

        SoilProfileEntity entity = soilMapper.toEntity(soil);

        assertThat(entity.getFieldCapacityGravimetricPct()).isEqualByComparingTo("28.50");
        assertThat(entity.getPermanentWiltingPointGravimetricPct()).isEqualByComparingTo("14.20");
        assertSameAggregate(soilMapper.toDomain(entity), soil);
    }

    @Test
    void soilProfileWithOnlyTypeAndDensityMapsEveryOptionalColumnToNull() {
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Mínimo", null, null, null, null,
                new BulkDensity(bd("1.30")), null, null, null, null, null, null);

        SoilProfileEntity entity = soilMapper.toEntity(soil);

        assertThat(entity.getFieldCapacity()).isNull();
        assertThat(entity.getPermanentWiltingPoint()).isNull();
        assertThat(entity.getFieldCapacityGravimetricPct()).isNull();
        assertThat(entity.getPermanentWiltingPointGravimetricPct()).isNull();
        assertThat(entity.getEffectiveRootDepth()).isNull();
        assertThat(entity.getAvailableWater()).isNull();
        assertThat(entity.getSoilWaterUnit()).isNull();
        assertThat(entity.getBulkDensityUnit()).isNull();
        assertThat(entity.getRootDepthUnit()).isNull();
        assertThat(entity.getAvailableWaterUnit()).isNull();
        assertSameAggregate(soilMapper.toDomain(entity), soil);
    }

    @Test
    void legacySoilProfileEntityWithoutGravimetricColumnsMapsToNullGravimetricPair() {
        SoilProfileEntity entity = soilMapper.toEntity(TestData.soilProfile());

        SoilProfile soil = soilMapper.toDomain(entity);

        assertThat(soil.getFieldCapacityGravimetric()).isNull();
        assertThat(soil.getPermanentWiltingPointGravimetric()).isNull();
        assertThat(soil.getFieldCapacity().value()).isEqualByComparingTo("0.35");
    }

    // =====================================================================================
    // Recomendação (snapshots JSONB)
    // =====================================================================================

    @Test
    void recommendationRoundTripsThroughTheJsonSnapshots() {
        Recommendation recommendation = TestData.recommendation(UUID.randomUUID(), LocalDateTime.of(2025, 3, 10, 8, 30));

        RecommendationEntity entity = recommendationMapper.toEntity(recommendation);

        assertThat(entity.getMethodologyId()).isEqualTo("fake");
        assertThat(entity.getMethodologyVersion()).isEqualTo("1.0");
        assertThat(entity.getInputData().kc()).isEqualByComparingTo("1.0");
        assertThat(entity.getResults().waterVolume()).isEqualByComparingTo("2000");
        assertSameAggregate(recommendationMapper.toDomain(entity), recommendation);
    }

    /** Kc e eficiência são opcionais no snapshot de entrada; ausentes, continuam ausentes. */
    @Test
    void recommendationWithoutKcAndEfficiencyRoundTrips() {
        CalculationInput input = new CalculationInput(bd("5"), null, bd("5"), bd("0"), bd("50"), bd("100"),
                null, bd("20"), bd("25"), bd("10"), bd("100"), bd("2"));
        Recommendation recommendation = new Recommendation(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2025, 1, 1, 0, 0), input, TestData.calculationResult(),
                new MethodologyIdentifier("fake"), "2.1");

        RecommendationEntity entity = recommendationMapper.toEntity(recommendation);

        assertThat(entity.getInputData().kc()).isNull();
        assertThat(entity.getInputData().irrigationEfficiency()).isNull();
        assertSameAggregate(recommendationMapper.toDomain(entity), recommendation);
    }
}
