package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.exception.PreconditionsNotMetException.Precondition;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.FakeAreaOperationLock;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.irrigation.support.TestData.fixedClock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propriedade 16: pré-condições agregadas do cálculo.
 *
 * <p>Para qualquer subconjunto das quatro pré-condições violadas (meteorologia ausente, Irrigação Real
 * ausente, campos do solo ausentes e campos da cultura ausentes, cada um com um subconjunto arbitrário de
 * campos), o cálculo falha com uma única {@link PreconditionsNotMetException}. A lista contém exatamente uma
 * entrada por pré-condição violada, sem omitir nem repetir, com os campos ausentes corretos, e nada é
 * gravado. Sem nenhuma violação, o cálculo ocorre. Irrigação Real igual a zero conta como presente.
 *
 * <p>Os campos do solo são anulados por reflexão, porque o agregado exige densidade aparente e o par
 * gravimétrico "ambos ou nenhum" na construção; o serviço precisa tolerar registros legados.
 *
 * <p>**Validates: Requirements 1.12, 2.8, 7.7, 11.1, 11.2, 11.3, 11.4, 11.5**
 */
class PreconditionsAggregationPropertyTest {

    // Feature: irrigation-water-balance-module, Property 16: Pré-condições agregadas do cálculo
    @Property(tries = 200)
    void aggregatesExactlyTheViolatedPreconditions(
            @ForAll boolean weatherMissing,
            @ForAll boolean irrigationMissing,
            @ForAll boolean soilCapacityMissing,
            @ForAll boolean soilWiltingMissing,
            @ForAll boolean soilDensityMissing,
            @ForAll boolean cropKcMissing,
            @ForAll boolean cropRootDepthMissing,
            @ForAll boolean zeroIrrigation) throws Exception {

        UUID areaId = UUID.randomUUID();
        LocalDate date = TestData.scenarioDate(1);

        SoilProfile soil = TestData.scenarioSoilProfile();
        nullField(soil, "fieldCapacityGravimetric", soilCapacityMissing);
        nullField(soil, "permanentWiltingPointGravimetric", soilWiltingMissing);
        nullField(soil, "bulkDensity", soilDensityMissing);

        Crop scenarioCrop = TestData.scenarioCrop();
        Crop crop = new Crop(scenarioCrop.getId(), scenarioCrop.getName(), null, 120, List.of(),
                cropKcMissing ? null : scenarioCrop.getKc(),
                cropRootDepthMissing ? null : scenarioCrop.getRootDepth());

        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        areas.save(TestData.area(areaId, "Talhão", "10.50"));
        InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository();
        ManagementPlan plan = plans.save(TestData.scenarioPlan(areaId, crop.getId(), soil.getId()));
        assertThat(plan.getStartDate()).isEqualTo(date);

        SoilProfileRepository soils = mock(SoilProfileRepository.class);
        when(soils.findById(soil.getId())).thenReturn(Optional.of(soil));
        CropRepository crops = mock(CropRepository.class);
        when(crops.findById(crop.getId())).thenReturn(Optional.of(crop));

        InMemoryDailyWeatherRepository weather = new InMemoryDailyWeatherRepository();
        if (!weatherMissing) {
            weather.upsert(TestData.dailyWeather(areaId, date, "5.00", "0"));
        }
        InMemoryAppliedIrrigationRepository irrigations = new InMemoryAppliedIrrigationRepository();
        if (!irrigationMissing) {
            irrigations.upsert(TestData.appliedIrrigation(areaId, date, zeroIrrigation ? "0" : "12"));
        }
        InMemoryDailyWaterBalanceRepository balances = new InMemoryDailyWaterBalanceRepository();

        WaterBalanceService service = new WaterBalanceService(areas, plans, soils, crops, weather, irrigations,
                balances, new FakeAreaOperationLock(), fixedClock());
        CalculateWaterBalanceInput input = new CalculateWaterBalanceInput(date, 1);

        Map<Code, List<String>> expected = new EnumMap<>(Code.class);
        if (weatherMissing) {
            expected.put(Code.WEATHER_MISSING, List.of());
        }
        if (irrigationMissing) {
            expected.put(Code.APPLIED_IRRIGATION_MISSING, List.of());
        }
        List<String> soilFields = new ArrayList<>();
        if (soilCapacityMissing) {
            soilFields.add("fieldCapacityGravimetric");
        }
        if (soilWiltingMissing) {
            soilFields.add("permanentWiltingPointGravimetric");
        }
        if (soilDensityMissing) {
            soilFields.add("bulkDensity");
        }
        if (!soilFields.isEmpty()) {
            expected.put(Code.SOIL_FIELDS_MISSING, soilFields);
        }
        List<String> cropFields = new ArrayList<>();
        if (cropKcMissing) {
            cropFields.add("kc");
        }
        if (cropRootDepthMissing) {
            cropFields.add("rootDepth");
        }
        if (!cropFields.isEmpty()) {
            expected.put(Code.CROP_FIELDS_MISSING, cropFields);
        }

        if (expected.isEmpty()) {
            service.calculate(areaId, input);
            assertThat(balances.count()).isEqualTo(1);
            return;
        }

        assertThatThrownBy(() -> service.calculate(areaId, input))
                .isInstanceOfSatisfying(PreconditionsNotMetException.class, e -> {
                    assertThat(e.getDate()).isEqualTo(date);
                    List<Precondition> actual = e.getPreconditions();
                    assertThat(actual).hasSameSizeAs(expected.keySet());
                    assertThat(actual).extracting(Precondition::code)
                            .containsExactlyInAnyOrderElementsOf(expected.keySet());
                    for (Precondition p : actual) {
                        assertThat(p.fields()).containsExactlyInAnyOrderElementsOf(expected.get(p.code()));
                    }
                });
        assertThat(balances.count()).isZero();
        assertThat(balances.findAll()).isEmpty();
    }

    private static void nullField(SoilProfile soil, String name, boolean doNull) throws Exception {
        if (!doNull) {
            return;
        }
        Field field = SoilProfile.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(soil, null);
    }
}
