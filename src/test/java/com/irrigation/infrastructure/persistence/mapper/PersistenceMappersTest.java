package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
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
