package com.irrigation.infrastructure.persistence;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
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
