package com.irrigation.domain.model;

import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras de negócio dos agregados: um caso válido e casos inválidos por regra (Req 10.1).
 */
class AggregatesTest {

    @Nested
    class AreaRules {

        @Test
        void createsValidAreaWithoutLocation() {
            Area area = TestData.area(UUID.randomUUID(), "Talhão A", "5.00");
            assertThat(area.getName()).isEqualTo("Talhão A");
            assertThat(area.getGeographicLocation()).isNull();
        }

        @Test
        void rejectsBlankName() {
            assertThatThrownBy(() -> TestData.area(UUID.randomUUID(), "   ", "5.00"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nome");
        }

        @Test
        void acceptsNameWith255CharactersAndRejects256() {
            assertThatCode(() -> TestData.area(UUID.randomUUID(), "a".repeat(255), "5.00")).doesNotThrowAnyException();
            assertThatThrownBy(() -> TestData.area(UUID.randomUUID(), "a".repeat(256), "5.00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void updateReplacesTheData() {
            Area area = TestData.area();
            area.update("Novo nome", new AreaInHectares(bd("20")), null, null, IrrigationSystemType.SPRINKLER,
                    new FlowRate(bd("50")), null);
            assertThat(area.getName()).isEqualTo("Novo nome");
            assertThat(area.getIrrigationSystemType()).isEqualTo(IrrigationSystemType.SPRINKLER);
        }

        @Test
        void updateRejectsBlankName() {
            Area area = TestData.area();
            assertThatThrownBy(() -> area.update("", new AreaInHectares(bd("20")), null, null,
                    IrrigationSystemType.SPRINKLER, new FlowRate(bd("50")), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CropRules {

        private CropStage stage(String name, String kc, int order) {
            return new CropStage(name, new Kc(bd(kc)), order);
        }

        @Test
        void createsValidCrop() {
            Crop crop = TestData.crop();
            assertThat(crop.getStages()).hasSize(2);
        }

        @Test
        void rejectsEmptyStageList() {
            assertThatThrownBy(() -> new Crop(UUID.randomUUID(), "Milho", null, null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsTwentyStagesAndRejectsTwentyOne() {
            assertThatCode(() -> new Crop(UUID.randomUUID(), "Milho", null, null, stages(20)))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> new Crop(UUID.randomUUID(), "Milho", null, null, stages(21)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsDuplicateStageOrder() {
            List<CropStage> stages = List.of(stage("A", "0.5", 1), stage("B", "0.9", 1));
            assertThatThrownBy(() -> new Crop(UUID.randomUUID(), "Milho", null, null, stages))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("única");
        }

        @Test
        void rejectsNonPositiveStageOrderAndBlankStageName() {
            assertThatThrownBy(() -> stage("A", "0.5", 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> stage(" ", "0.5", 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> stage("a".repeat(101), "0.5", 1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankCropName() {
            assertThatThrownBy(() -> new Crop(UUID.randomUUID(), " ", null, null, stages(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void exposedStageListIsReadOnly() {
            Crop crop = TestData.crop();
            assertThatThrownBy(() -> crop.getStages().add(stage("X", "1.0", 9)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void updateReplacesStages() {
            Crop crop = TestData.crop();
            crop.update("Soja", null, 100, List.of(stage("Único", "1.0", 1)));
            assertThat(crop.getName()).isEqualTo("Soja");
            assertThat(crop.getStages()).hasSize(1);
        }

        private List<CropStage> stages(int count) {
            List<CropStage> list = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                list.add(stage("Estágio " + i, "1.0", i));
            }
            return list;
        }
    }

    @Nested
    class SoilProfileRules {

        private SoilProfile soil(String fieldCapacity, String wiltingPoint, MeasurementUnit waterUnit) {
            return new SoilProfile(UUID.randomUUID(), "Arenoso",
                    new VolumetricWaterContent(bd(fieldCapacity)), new VolumetricWaterContent(bd(wiltingPoint)),
                    new BulkDensity(bd("1.5")), new RootDepth(bd("500")), new AvailableWater(bd("80")),
                    waterUnit, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                    MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);
        }

        @Test
        void createsValidProfile() {
            SoilProfile soil = TestData.soilProfile();
            assertThat(soil.getSoilWaterUnit()).isEqualTo(MeasurementUnit.VOLUMETRIC_WATER_CONTENT);
        }

        @Test
        void rejectsFieldCapacityEqualOrBelowWiltingPoint() {
            assertThatThrownBy(() -> soil("0.20", "0.20", MeasurementUnit.PERCENT))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacidade de campo");
            assertThatThrownBy(() -> soil("0.10", "0.20", MeasurementUnit.PERCENT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsMissingUnit() {
            assertThatThrownBy(() -> soil("0.30", "0.10", null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsBlankSoilType() {
            assertThatThrownBy(() -> new SoilProfile(UUID.randomUUID(), " ",
                    new VolumetricWaterContent(bd("0.3")), new VolumetricWaterContent(bd("0.1")),
                    new BulkDensity(bd("1.5")), new RootDepth(bd("500")), new AvailableWater(bd("80")),
                    MeasurementUnit.PERCENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                    MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RecommendationRules {

        @Test
        void keepsAllSnapshotFields() {
            UUID areaId = UUID.randomUUID();
            LocalDateTime when = LocalDateTime.of(2025, 1, 15, 10, 30);
            Recommendation r = TestData.recommendation(areaId, when);
            assertThat(r.getAreaId()).isEqualTo(areaId);
            assertThat(r.getDateTime()).isEqualTo(when);
            assertThat(r.getMethodologyId().value()).isEqualTo("fake");
            assertThat(r.getMethodologyVersion()).isEqualTo("1.0");
            assertThat(r.getResults()).isEqualTo(TestData.calculationResult());
        }

        @Test
        void rejectsMissingRequiredFields() {
            assertThatThrownBy(() -> new Recommendation(UUID.randomUUID(), null, LocalDateTime.now(),
                    TestData.calculationInput(), TestData.calculationResult(),
                    new com.irrigation.domain.calculation.MethodologyIdentifier("m"), "1"))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
