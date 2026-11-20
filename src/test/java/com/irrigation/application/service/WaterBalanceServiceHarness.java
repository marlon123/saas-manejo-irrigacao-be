package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.ReprocessWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.FakeAreaOperationLock;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;

/**
 * Montagem compartilhada dos testes de {@link WaterBalanceService} e {@link WaterBalanceQueryService}:
 * repositórios em memória, bloqueio falso e o Cenário A (requirements.md, seção 6) já cadastrado.
 * Não altera as fixtures compartilhadas.
 */
final class WaterBalanceServiceHarness {

    final InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
    final InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
    final InMemoryDailyWaterBalanceRepository balances = new InMemoryDailyWaterBalanceRepository();
    final InMemoryManagementPlanRepository plans = new InMemoryManagementPlanRepository().connectBalances(balances);
    final MapSoilRepository soils = new MapSoilRepository();
    final MapCropRepository crops = new MapCropRepository();
    final HideableWeatherRepository weather = new HideableWeatherRepository();
    final HideableIrrigationRepository irrigation = new HideableIrrigationRepository();
    final FakeAreaOperationLock lock = new FakeAreaOperationLock();

    final WaterBalanceService service;
    final WaterBalanceQueryService query;

    UUID areaId;
    SoilProfile soil;
    Crop crop;
    ManagementPlan plan;

    WaterBalanceServiceHarness() {
        service = new WaterBalanceService(areas, plans, soils, crops, weather, irrigation, balances, lock,
                TestData.fixedClock());
        query = new WaterBalanceQueryService(balances, areas);
    }

    /** Cenário A: área, solo, cultura e plano (F = 0,50, 6 mm/h), com meteorologia e IR dos {@code days} dias. */
    static WaterBalanceServiceHarness scenarioA(int days) {
        WaterBalanceServiceHarness h = new WaterBalanceServiceHarness();
        h.registerBase();
        h.seedScenarioDays(days);
        return h;
    }

    /** Cadastra área, solo, cultura e plano do Cenário A, sem meteorologia nem IR. */
    void registerBase() {
        Area area = TestData.area();
        areas.save(area);
        areaId = area.getId();
        soil = TestData.scenarioSoilProfile();
        soils.save(soil);
        crop = TestData.scenarioCrop();
        crops.save(crop);
        plan = TestData.scenarioPlan(areaId, crop.getId(), soil.getId());
        plans.save(plan);
    }

    /** Meteorologia e IR (inclusive os dias com IR zero) dos dias 1 a {@code days} do Cenário A. */
    void seedScenarioDays(int days) {
        for (DailyWeather w : TestData.scenarioWeather(areaId, days)) {
            weather.upsert(w);
        }
        for (int i = 0; i < days; i++) {
            irrigation.upsert(TestData.appliedIrrigation(areaId, TestData.scenarioDate(i + 1),
                    TestData.SCENARIO_A_IRRIGATION.get(i)));
        }
    }

    /**
     * Encerra o plano do Cenário A no dia anterior e cria um segundo plano (mesma cultura e solo, F = 0,50)
     * que começa no dia {@code startDay} do cenário, como na troca de cultura ou solo (DP-06).
     */
    ManagementPlan startSecondPlanAt(int startDay) {
        plan.close(TestData.scenarioDate(startDay - 1));
        ManagementPlan second = TestData.managementPlan(areaId, crop.getId(), soil.getId(),
                TestData.scenarioDate(startDay));
        plans.save(second);
        return second;
    }

    /** Altera o fator F do plano (mesmo plano, como na atualização de parâmetros do Req 4.5). */
    void replacePlanWithFactor(String f) {
        plan.updateParameters(new AvailabilityFactor(bd(f)), plan.getSprinklerRate());
    }

    /**
     * Solo do plano substituído, mesmo id. CC e PMP gravimétricos são um par (ambos ou nenhum) e D é obrigatório
     * no agregado; o solo "sem os campos" é, portanto, o legado com o par gravimétrico nulo.
     */
    void replaceSoil(String fieldCapacity, String wiltingPoint, String bulkDensity) {
        soil = new SoilProfile(soil.getId(), "Solo editado", null, null,
                fieldCapacity == null ? null : new GravimetricWaterContent(bd(fieldCapacity)),
                wiltingPoint == null ? null : new GravimetricWaterContent(bd(wiltingPoint)),
                bulkDensity == null ? null : new BulkDensity(bd(bulkDensity)),
                null, null, null, null, null, null);
        soils.save(soil);
    }

    /** Cultura do plano substituída por uma com os campos informados (nulos = ausentes); mesmo id. */
    void replaceCrop(String kc, String rootDepth) {
        crop = new Crop(crop.getId(), "Cultura editada", null, 120, List.of(),
                kc == null ? null : new Kc(bd(kc)),
                rootDepth == null ? null : new RootDepth(bd(rootDepth)));
        crops.save(crop);
    }

    /** Corrige a ETP e a precipitação do dia (upsert). */
    void setWeather(int dayNumber, String etp, String precipitation) {
        weather.upsert(TestData.dailyWeather(areaId, TestData.scenarioDate(dayNumber), etp, precipitation));
    }

    /** Calcula o dia do Cenário A com o TR da tabela. */
    WaterBalanceOutput calculateDay(int dayNumber) {
        return service.calculate(areaId, new CalculateWaterBalanceInput(TestData.scenarioDate(dayNumber),
                TestData.SCENARIO_A_CYCLE_DAYS.get(dayNumber - 1)));
    }

    /** Calcula os dias 1 a {@code days} em ordem. */
    void calculateDays(int days) {
        for (int d = 1; d <= days; d++) {
            calculateDay(d);
        }
    }

    /** Reprocessa a partir do dia, dentro de uma transação simulada (desfaz tudo se falhar). */
    List<WaterBalanceOutput> reprocessFrom(int dayNumber) {
        return balances.atomically(() -> service.reprocess(areaId,
                new ReprocessWaterBalanceInput(TestData.scenarioDate(dayNumber))));
    }

    DailyWaterBalance current(int dayNumber) {
        return balances.findCurrent(areaId, TestData.scenarioDate(dayNumber)).orElseThrow();
    }

    // ------------------------------------------------------------ Repositórios auxiliares

    /** Solos em memória. */
    static final class MapSoilRepository implements SoilProfileRepository {
        private final Map<UUID, SoilProfile> store = new LinkedHashMap<>();

        @Override
        public SoilProfile save(SoilProfile soilProfile) {
            store.put(soilProfile.getId(), soilProfile);
            return soilProfile;
        }

        @Override
        public Optional<SoilProfile> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public boolean existsById(UUID id) {
            return store.containsKey(id);
        }

        @Override
        public List<SoilProfile> findAll(PageRequest pageRequest) {
            return new ArrayList<>(store.values());
        }

        @Override
        public long count() {
            return store.size();
        }
    }

    /** Culturas em memória. */
    static final class MapCropRepository implements CropRepository {
        private final Map<UUID, Crop> store = new LinkedHashMap<>();

        @Override
        public Crop save(Crop crop) {
            store.put(crop.getId(), crop);
            return crop;
        }

        @Override
        public Optional<Crop> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public boolean existsById(UUID id) {
            return store.containsKey(id);
        }

        @Override
        public List<Crop> findAll(PageRequest pageRequest) {
            return new ArrayList<>(store.values());
        }

        @Override
        public long count() {
            return store.size();
        }
    }

    /** Meteorologia em memória que permite "apagar" datas, para simular a ausência do dado. */
    static final class HideableWeatherRepository extends InMemoryDailyWeatherRepository {
        private final Set<LocalDate> hidden = new HashSet<>();

        void hide(LocalDate date) {
            hidden.add(date);
        }

        @Override
        public Optional<DailyWeather> findByAreaAndDate(UUID areaId, LocalDate date) {
            return hidden.contains(date) ? Optional.empty() : super.findByAreaAndDate(areaId, date);
        }
    }

    /** Irrigação aplicada em memória que permite "apagar" datas. */
    static final class HideableIrrigationRepository extends InMemoryAppliedIrrigationRepository {
        private final Set<LocalDate> hidden = new HashSet<>();

        void hide(LocalDate date) {
            hidden.add(date);
        }

        @Override
        public Optional<AppliedIrrigation> findByAreaAndDate(UUID areaId, LocalDate date) {
            return hidden.contains(date) ? Optional.empty() : super.findByAreaAndDate(areaId, date);
        }
    }
}
