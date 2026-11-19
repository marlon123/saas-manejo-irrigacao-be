package com.irrigation.infrastructure.persistence;

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
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip das portas de repositório contra o PostgreSQL real: o agregado salvo é o
 * agregado recuperado, incluindo coleções, enums e as colunas JSONB.
 */
class RepositoryRoundTripIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    AreaRepository areas;
    @Autowired
    CropRepository crops;
    @Autowired
    SoilProfileRepository soilProfiles;
    @Autowired
    RecommendationRepository recommendations;

    /** Compara campo a campo; BigDecimal por valor, pois o banco normaliza a escala (10.5 → 10.50). */
    private static <T> void assertSameAggregate(T actual, T expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    void areaWithAllFieldsRoundTrips() {
        Area area = new Area(UUID.randomUUID(), "Pivô Norte", new AreaInHectares(bd("52.75")),
                new GeographicLocation(bd("-15.793889"), bd("-47.882778")), "latossolo vermelho",
                IrrigationSystemType.CENTER_PIVOT, new FlowRate(bd("310.40")), new IrrigationEfficiency(bd("0.9")));

        areas.save(area);

        assertSameAggregate(areas.findById(area.getId()).orElseThrow(), area);
    }

    @Test
    void areaWithoutOptionalFieldsRoundTrips() {
        Area area = new Area(UUID.randomUUID(), "Talhão sem coordenadas", new AreaInHectares(bd("3")),
                null, null, IrrigationSystemType.FURROW, new FlowRate(bd("15")), null);

        areas.save(area);

        assertSameAggregate(areas.findById(area.getId()).orElseThrow(), area);
    }

    @Test
    void cropWithStagesRoundTripsInStageOrder() {
        Crop crop = TestData.crop();

        crops.save(crop);

        Crop found = crops.findById(crop.getId()).orElseThrow();
        assertSameAggregate(found, crop);
        assertThat(found.getStages()).extracting(CropStage::getStageOrder).containsExactly(1, 2);
    }

    /**
     * Trocar os estágios reaproveita as mesmas ordens. O Hibernate insere os novos estágios
     * antes de remover os antigos; só funciona porque a V2 tornou uq_crop_stage_order DEFERRABLE.
     */
    @Test
    void replacingStagesWithTheSameOrdersSucceeds() {
        Crop crop = TestData.crop();
        crops.save(crop);

        crop.update("Milho safrinha", "Híbrido", 110, List.of(
                new CropStage("Vegetativo", new Kc(bd("0.70")), 1),
                new CropStage("Reprodutivo", new Kc(bd("1.20")), 2),
                new CropStage("Maturação", new Kc(bd("0.60")), 3)));
        crops.save(crop);

        Crop found = crops.findById(crop.getId()).orElseThrow();
        assertSameAggregate(found, crop);
        Integer stageRows = jdbc.queryForObject(
                "SELECT count(*) FROM crop_stages WHERE crop_id = ?", Integer.class, crop.getId());
        assertThat(stageRows).isEqualTo(3);
    }

    @Test
    void soilProfileRoundTripsIncludingUnits() {
        SoilProfile soil = TestData.soilProfile();

        soilProfiles.save(soil);

        assertSameAggregate(soilProfiles.findById(soil.getId()).orElseThrow(), soil);
    }

    // =====================================================================================
    // Módulo de balanço hídrico: campos nulos e campos novos (Req 1.10, 1.11, 2.7, 3.4, 16.1)
    // =====================================================================================

    @Test
    void areaWithOnlyTheNameRoundTripsWithNullColumns() {
        Area area = new Area(UUID.randomUUID(), "Só o nome", null, null, null, null, null, null);

        areas.save(area);

        assertSameAggregate(areas.findById(area.getId()).orElseThrow(), area);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT area_ha, irrigation_system_type, system_flow_rate FROM areas WHERE id = ?", area.getId());
        assertThat(row.values()).containsOnlyNulls();
    }

    @Test
    void cropWithKcAndRootDepthRoundTripsAndFillsTheNewColumns() {
        Crop crop = new Crop(UUID.randomUUID(), "Milho", "Híbrido", 120,
                List.of(new CropStage("Inicial", new Kc(bd("0.40")), 1)), new Kc(bd("1.15")), new RootDepth(bd("450.50")));

        crops.save(crop);

        assertSameAggregate(crops.findById(crop.getId()).orElseThrow(), crop);
        Map<String, Object> row = jdbc.queryForMap("SELECT kc, root_depth_mm FROM crops WHERE id = ?", crop.getId());
        assertThat((BigDecimal) row.get("kc")).isEqualByComparingTo("1.15");
        assertThat((BigDecimal) row.get("root_depth_mm")).isEqualByComparingTo("450.50");
    }

    @Test
    void cropWithZeroStagesRoundTripsAndStoresNoStageRows() {
        Crop crop = new Crop(UUID.randomUUID(), "Sem estágios", null, null, List.of(),
                new Kc(bd("1.00")), new RootDepth(bd("400")));

        crops.save(crop);

        Crop found = crops.findById(crop.getId()).orElseThrow();
        assertThat(found.getStages()).isEmpty();
        assertSameAggregate(found, crop);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crop_stages WHERE crop_id = ?", Integer.class,
                crop.getId())).isZero();
    }

    @Test
    void replacingAllStagesByNoneAndBackSucceeds() {
        Crop crop = TestData.crop();
        crops.save(crop);

        crop.update("Milho", "Híbrido", 120, List.of(), new Kc(bd("1.00")), new RootDepth(bd("400")));
        crops.save(crop);
        assertThat(crops.findById(crop.getId()).orElseThrow().getStages()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crop_stages WHERE crop_id = ?", Integer.class,
                crop.getId())).isZero();

        crop.update("Milho", "Híbrido", 120, List.of(new CropStage("Pleno", new Kc(bd("1.15")), 1)),
                new Kc(bd("1.00")), new RootDepth(bd("400")));
        crops.save(crop);
        assertSameAggregate(crops.findById(crop.getId()).orElseThrow(), crop);
    }

    @Test
    void soilProfileWithGravimetricPairRoundTripsAndFillsTheNewColumns() {
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Argiloso",
                new VolumetricWaterContent(bd("0.35")), new VolumetricWaterContent(bd("0.18")),
                new GravimetricWaterContent(bd("28.50")), new GravimetricWaterContent(bd("14.20")),
                new BulkDensity(bd("1.30")), new RootDepth(bd("600.00")), new AvailableWater(bd("100.00")),
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);

        soilProfiles.save(soil);

        assertSameAggregate(soilProfiles.findById(soil.getId()).orElseThrow(), soil);
        Map<String, Object> row = jdbc.queryForMap("SELECT field_capacity_gravimetric_pct AS fc, "
                + "permanent_wilting_point_gravimetric_pct AS pwp FROM soil_profiles WHERE id = ?", soil.getId());
        assertThat((BigDecimal) row.get("fc")).isEqualByComparingTo("28.50");
        assertThat((BigDecimal) row.get("pwp")).isEqualByComparingTo("14.20");
    }

    @Test
    void soilProfileWithOnlyTypeAndDensityRoundTripsWithNullColumns() {
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Mínimo", null, null, null, null,
                new BulkDensity(bd("1.30")), null, null, null, null, null, null);

        soilProfiles.save(soil);

        SoilProfile found = soilProfiles.findById(soil.getId()).orElseThrow();
        assertSameAggregate(found, soil);
        assertThat(found.getFieldCapacity()).isNull();
        assertThat(found.getFieldCapacityGravimetric()).isNull();
        assertThat(found.getSoilWaterUnit()).isNull();
    }

    @Test
    void updatingASoilProfileToDropTheOptionalFieldsPersistsNulls() {
        SoilProfile soil = TestData.soilProfile();
        soilProfiles.save(soil);

        soil.update("Argiloso", null, null, null, null, new BulkDensity(bd("1.30")), null, null, null, null, null, null);
        soilProfiles.save(soil);

        assertSameAggregate(soilProfiles.findById(soil.getId()).orElseThrow(), soil);
    }

    /** Req 3.1 e 16.1: nulos no fim nas duas direções, e o desempate por id mantém a paginação estável. */
    @Test
    void areasSortedByHectaresPutNullsLastInBothDirections() {
        Area small = TestData.area(UUID.randomUUID(), "Pequena", "5.00");
        Area large = TestData.area(UUID.randomUUID(), "Grande", "50.00");
        Area unknownA = new Area(UUID.randomUUID(), "Sem área A", null, null, null, null, null, null);
        Area unknownB = new Area(UUID.randomUUID(), "Sem área B", null, null, null, null, null, null);
        List.of(unknownA, large, unknownB, small).forEach(areas::save);

        List<Area> ascending = areas.findAll(PageRequest.of(0, 10), "areaHectares", "asc");
        List<Area> descending = areas.findAll(PageRequest.of(0, 10), "areaHectares", "desc");

        assertThat(ascending).extracting(Area::getName).startsWith("Pequena", "Grande");
        assertThat(ascending.subList(2, 4)).extracting(Area::getName)
                .containsExactlyInAnyOrder("Sem área A", "Sem área B");
        assertThat(descending).extracting(Area::getName).startsWith("Grande", "Pequena");
        assertThat(descending.subList(2, 4)).extracting(Area::getName)
                .containsExactlyInAnyOrder("Sem área A", "Sem área B");
        // paginação: a segunda página (tamanho 2) só traz as áreas sem valor, nas duas direções
        assertThat(areas.findAll(PageRequest.of(1, 2), "areaHectares", "asc")).extracting(Area::getName)
                .containsExactlyInAnyOrder("Sem área A", "Sem área B");
        assertThat(areas.findAll(PageRequest.of(1, 2), "areaHectares", "desc")).extracting(Area::getName)
                .containsExactlyInAnyOrder("Sem área A", "Sem área B");
    }

    @Test
    void recommendationRoundTripsThroughJsonbColumns() {
        Area area = areas.save(TestData.area());
        Recommendation recommendation = TestData.recommendation(area.getId(), LocalDateTime.of(2025, 3, 10, 8, 30));

        recommendations.save(recommendation);

        assertSameAggregate(recommendations.findById(recommendation.getId()).orElseThrow(), recommendation);
    }

    @Test
    void recommendationSnapshotIsStoredAsJsonbObjects() {
        Area area = areas.save(TestData.area());
        Recommendation recommendation = recommendations.save(
                TestData.recommendation(area.getId(), LocalDateTime.of(2025, 3, 10, 8, 30)));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT jsonb_typeof(input_data) AS input_type, jsonb_typeof(results) AS results_type, "
                        + "(input_data ->> 'eto')::numeric AS eto, (results ->> 'waterVolume')::numeric AS volume "
                        + "FROM recommendations WHERE id = ?", recommendation.getId());

        assertThat(row.get("input_type")).isEqualTo("object");
        assertThat(row.get("results_type")).isEqualTo("object");
        assertThat((BigDecimal) row.get("eto")).isEqualByComparingTo("5");
        assertThat((BigDecimal) row.get("volume")).isEqualByComparingTo("2000");
    }
}
