package com.irrigation.infrastructure.persistence;

import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.repository.AppliedIrrigationRepository;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.DailyWeatherRepository;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistência do plano de manejo, da meteorologia diária e da Irrigação Real contra o PostgreSQL real:
 * round trip dos agregados, concorrência (Req 4.7, 4.8, 5.7, 6.7) e exclusão de cadastros referenciados
 * (Req 1.13, 2.9, 3.5).
 *
 * <p>Os testes não são transacionais: cada chamada de repositório tem a própria transação,
 * como em produção, e as threads concorrentes usam conexões distintas.
 */
class WaterBalanceInputsPersistenceIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Autowired
    AreaRepository areas;
    @Autowired
    CropRepository crops;
    @Autowired
    SoilProfileRepository soilProfiles;
    @Autowired
    ManagementPlanRepository plans;
    @Autowired
    DailyWeatherRepository weathers;
    @Autowired
    AppliedIrrigationRepository irrigations;

    private Area newArea() {
        return areas.save(TestData.area(UUID.randomUUID(), "Talhão " + UUID.randomUUID(), "10.50"));
    }

    private Crop newCrop() {
        return crops.save(TestData.crop());
    }

    private SoilProfile newSoil() {
        return soilProfiles.save(TestData.soilProfile());
    }

    private static <T> void assertSameAggregate(T actual, T expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** Executa as duas tarefas ao mesmo tempo (largada sincronizada) e devolve o resultado ou a exceção de cada uma. */
    private static <T> List<Object> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return task.call();
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------------------------

    @Test
    void activePlanRoundTripsAndIsFoundByTheQueries() {
        Area area = newArea();
        ManagementPlan plan = TestData.managementPlan(area.getId(), newCrop().getId(), newSoil().getId(), DAY);

        plans.save(plan);

        assertSameAggregate(plans.findById(plan.getId()).orElseThrow(), plan);
        assertSameAggregate(plans.findActiveByArea(area.getId()).orElseThrow(), plan);
        assertSameAggregate(plans.findCoveringDate(area.getId(), DAY.plusDays(30)).orElseThrow(), plan);
        assertThat(plans.findCoveringDate(area.getId(), DAY.minusDays(1))).isEmpty();
        assertThat(plans.findByAreaOrderByStartDateDesc(area.getId())).hasSize(1);
    }

    @Test
    void updatingParametersAndClosingThePlanPersistsAndFreesTheArea() {
        Area area = newArea();
        UUID cropId = newCrop().getId();
        UUID soilId = newSoil().getId();
        ManagementPlan plan = plans.save(TestData.managementPlan(area.getId(), cropId, soilId, DAY));

        plan.updateParameters(new AvailabilityFactor(bd("0.65")), new SprinklerRate(bd("12.5")));
        plan.close(DAY.plusDays(20));
        plans.save(plan);

        assertSameAggregate(plans.findById(plan.getId()).orElseThrow(), plan);
        assertThat(plans.findActiveByArea(area.getId())).isEmpty();
        assertThat(count("management_plans")).isEqualTo(1);

        ManagementPlan next = TestData.managementPlan(area.getId(), cropId, soilId, DAY.plusDays(21));
        plans.save(next);
        assertThat(plans.findByAreaOrderByStartDateDesc(area.getId())).extracting(ManagementPlan::getId)
                .containsExactly(next.getId(), plan.getId());
        assertThat(plans.findCoveringDate(area.getId(), DAY.plusDays(20)).orElseThrow().getId()).isEqualTo(plan.getId());
    }

    @Test
    void secondActivePlanForTheSameAreaIsTranslatedToBusinessRuleException() {
        Area area = newArea();
        UUID cropId = newCrop().getId();
        UUID soilId = newSoil().getId();
        plans.save(TestData.managementPlan(area.getId(), cropId, soilId, DAY));

        assertThatThrownBy(() -> plans.save(TestData.managementPlan(area.getId(), cropId, soilId, DAY.plusDays(5))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(count("management_plans")).isEqualTo(1);
    }

    @Test
    void dailyWeatherRoundTripsAndUpsertReplacesKeepingTheOriginalId() {
        Area area = newArea();
        DailyWeather first = TestData.dailyWeather(area.getId(), DAY, "5.25", "0");

        DailyWeather saved = weathers.upsert(first);
        assertSameAggregate(saved, first);
        assertSameAggregate(weathers.findByAreaAndDate(area.getId(), DAY).orElseThrow(), first);

        DailyWeather replacement = TestData.dailyWeather(area.getId(), DAY, "7.5", "12.35");
        DailyWeather replaced = weathers.upsert(replacement);

        assertThat(replaced.getId()).isEqualTo(first.getId());
        assertThat(replaced.getEtp().value()).isEqualByComparingTo("7.5");
        assertThat(replaced.getPrecipitation().value()).isEqualByComparingTo("12.35");
        assertThat(count("daily_weather")).isEqualTo(1);
    }

    @Test
    void appliedIrrigationRoundTripsAndUpsertReplacesKeepingTheOriginalId() {
        Area area = newArea();
        AppliedIrrigation first = TestData.appliedIrrigation(area.getId(), DAY, "0");

        assertSameAggregate(irrigations.upsert(first), first);
        assertSameAggregate(irrigations.findByAreaAndDate(area.getId(), DAY).orElseThrow(), first);

        AppliedIrrigation replaced = irrigations.upsert(TestData.appliedIrrigation(area.getId(), DAY, "18.75"));

        assertThat(replaced.getId()).isEqualTo(first.getId());
        assertThat(replaced.getDepth().value()).isEqualByComparingTo("18.75");
        assertThat(count("applied_irrigation")).isEqualTo(1);
    }

    @Test
    void periodQueriesAreInclusiveAndOrderedByDateAscending() {
        Area area = newArea();
        Area other = newArea();
        for (int offset : new int[] {4, 0, 2, 1, 3}) {
            weathers.upsert(TestData.dailyWeather(area.getId(), DAY.plusDays(offset), "1", "0"));
            irrigations.upsert(TestData.appliedIrrigation(area.getId(), DAY.plusDays(offset), "2"));
        }
        weathers.upsert(TestData.dailyWeather(other.getId(), DAY.plusDays(1), "9", "9"));
        irrigations.upsert(TestData.appliedIrrigation(other.getId(), DAY.plusDays(1), "9"));

        assertThat(weathers.findByAreaAndPeriod(area.getId(), DAY.plusDays(1), DAY.plusDays(3)))
                .extracting(DailyWeather::getDate).containsExactly(DAY.plusDays(1), DAY.plusDays(2), DAY.plusDays(3));
        assertThat(irrigations.findByAreaAndPeriod(area.getId(), DAY, DAY.plusDays(1)))
                .extracting(AppliedIrrigation::getDate).containsExactly(DAY, DAY.plusDays(1));
        assertThat(weathers.findByAreaAndPeriod(area.getId(), DAY.plusDays(10), DAY.plusDays(20))).isEmpty();
    }

    /**
     * As colunas de meteorologia e IR são NUMERIC(8,3) e as de plano NUMERIC(5,4) e NUMERIC(10,4): o banco devolve
     * 5,250 para 5.25. O value object conta casas sem os zeros à direita, então o valor lido continua válido
     * e numericamente igual ao gravado, inclusive nos limites.
     */
    @Test
    void valuesReadBackWithTheColumnScaleAreAcceptedAndNumericallyEqual() {
        Area area = newArea();
        UUID cropId = newCrop().getId();
        UUID soilId = newSoil().getId();

        weathers.upsert(TestData.dailyWeather(area.getId(), DAY, "1000", "0.01"));
        irrigations.upsert(TestData.appliedIrrigation(area.getId(), DAY, "1000"));
        ManagementPlan plan = plans.save(new ManagementPlan(UUID.randomUUID(), area.getId(), cropId, soilId, DAY,
                new AvailabilityFactor(bd("0.0001")), new SprinklerRate(bd("500"))));

        BigDecimal etpColumn = jdbc.queryForObject("SELECT etp_mm FROM daily_weather", BigDecimal.class);
        BigDecimal precipitationColumn = jdbc.queryForObject("SELECT precipitation_mm FROM daily_weather",
                BigDecimal.class);
        BigDecimal irrigationColumn = jdbc.queryForObject("SELECT applied_irrigation_mm FROM applied_irrigation",
                BigDecimal.class);
        assertThat(etpColumn.scale()).isEqualTo(3);
        assertThat(precipitationColumn.scale()).isEqualTo(3);
        assertThat(irrigationColumn.scale()).isEqualTo(3);

        DailyWeather weather = weathers.findByAreaAndDate(area.getId(), DAY).orElseThrow();
        assertThat(weather.getEtp().value()).isEqualByComparingTo("1000");
        assertThat(weather.getPrecipitation().value()).isEqualByComparingTo("0.01");
        assertThat(irrigations.findByAreaAndDate(area.getId(), DAY).orElseThrow().getDepth().value())
                .isEqualByComparingTo("1000");
        ManagementPlan found = plans.findById(plan.getId()).orElseThrow();
        assertThat(found.getAvailabilityFactor().value()).isEqualByComparingTo("0.0001");
        assertThat(found.getSprinklerRate().value()).isEqualByComparingTo("500");
    }

    // ------------------------------------------------------------------------------------
    // Concorrência
    // ------------------------------------------------------------------------------------

    @Test
    void concurrentWeatherUpsertsForTheSameKeyLeaveOneRow() throws Exception {
        Area area = newArea();
        int rounds = 15;
        for (int round = 0; round < rounds; round++) {
            LocalDate date = DAY.plusDays(round);
            DailyWeather a = TestData.dailyWeather(area.getId(), date, "1", "0");
            DailyWeather b = TestData.dailyWeather(area.getId(), date, "2", "3");

            List<Object> outcomes = race(() -> weathers.upsert(a), () -> weathers.upsert(b));

            assertThat(outcomes).as("rodada %d", round).allSatisfy(o -> assertThat(o).isInstanceOf(DailyWeather.class));
            List<DailyWeather> rows = weathers.findByAreaAndPeriod(area.getId(), date, date);
            assertThat(rows).hasSize(1);
            DailyWeather row = rows.get(0);
            assertThat(row.getId()).isIn(a.getId(), b.getId());
            boolean matchesA = row.getEtp().value().compareTo(bd("1")) == 0
                    && row.getPrecipitation().value().compareTo(bd("0")) == 0;
            boolean matchesB = row.getEtp().value().compareTo(bd("2")) == 0
                    && row.getPrecipitation().value().compareTo(bd("3")) == 0;
            assertThat(matchesA || matchesB).as("valores de uma das gravações, sem mistura").isTrue();
        }
        assertThat(count("daily_weather")).isEqualTo(rounds);
    }

    @Test
    void concurrentAppliedIrrigationUpsertsForTheSameKeyLeaveOneRow() throws Exception {
        Area area = newArea();
        int rounds = 15;
        for (int round = 0; round < rounds; round++) {
            LocalDate date = DAY.plusDays(round);
            AppliedIrrigation a = TestData.appliedIrrigation(area.getId(), date, "10");
            AppliedIrrigation b = TestData.appliedIrrigation(area.getId(), date, "20.5");

            List<Object> outcomes = race(() -> irrigations.upsert(a), () -> irrigations.upsert(b));

            assertThat(outcomes).as("rodada %d", round)
                    .allSatisfy(o -> assertThat(o).isInstanceOf(AppliedIrrigation.class));
            List<AppliedIrrigation> rows = irrigations.findByAreaAndPeriod(area.getId(), date, date);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getId()).isIn(a.getId(), b.getId());
            BigDecimal depth = rows.get(0).getDepth().value();
            assertThat(depth.compareTo(bd("10")) == 0 || depth.compareTo(bd("20.5")) == 0).isTrue();
        }
        assertThat(count("applied_irrigation")).isEqualTo(rounds);
    }

    @Test
    void concurrentCreationsOfAnActivePlanForTheSameAreaSucceedOnlyOnce() throws Exception {
        UUID cropId = newCrop().getId();
        UUID soilId = newSoil().getId();
        for (int round = 0; round < 10; round++) {
            Area area = newArea();
            ManagementPlan a = TestData.managementPlan(area.getId(), cropId, soilId, DAY);
            ManagementPlan b = TestData.managementPlan(area.getId(), cropId, soilId, DAY.plusDays(1));

            List<Object> outcomes = race(() -> plans.save(a), () -> plans.save(b));

            assertThat(outcomes.stream().filter(ManagementPlan.class::isInstance).count())
                    .as("rodada %d: criações bem-sucedidas", round).isEqualTo(1);
            assertThat(outcomes.stream().filter(BusinessRuleException.class::isInstance).count())
                    .as("rodada %d: recusas por regra de negócio", round).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM management_plans WHERE area_id = ?",
                    Integer.class, area.getId())).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------------------------
    // Exclusão de cadastros referenciados
    // ------------------------------------------------------------------------------------

    @Test
    void deletingAnAreaReferencedByAPlanIsRefused() {
        Area area = newArea();
        plans.save(TestData.managementPlan(area.getId(), newCrop().getId(), newSoil().getId(), DAY));

        assertThatThrownBy(() -> areas.deleteById(area.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(area.getId().toString())
                .hasMessageContaining("planos de manejo");
        assertThat(areas.existsById(area.getId())).isTrue();
        assertThat(count("management_plans")).isEqualTo(1);
    }

    @Test
    void deletingAnAreaReferencedByWeatherIsRefused() {
        Area area = newArea();
        weathers.upsert(TestData.dailyWeather(area.getId(), DAY, "5", "0"));

        assertThatThrownBy(() -> areas.deleteById(area.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dados meteorológicos diários");
        assertThat(areas.existsById(area.getId())).isTrue();
        assertThat(count("daily_weather")).isEqualTo(1);
    }

    @Test
    void deletingAnAreaReferencedByAppliedIrrigationIsRefused() {
        Area area = newArea();
        irrigations.upsert(TestData.appliedIrrigation(area.getId(), DAY, "0"));

        assertThatThrownBy(() -> areas.deleteById(area.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("irrigações reais");
        assertThat(areas.existsById(area.getId())).isTrue();
        assertThat(count("applied_irrigation")).isEqualTo(1);
    }

    @Test
    void deletingASoilProfileReferencedByAPlanIsRefused() {
        SoilProfile soil = newSoil();
        plans.save(TestData.managementPlan(newArea().getId(), newCrop().getId(), soil.getId(), DAY));

        assertThatThrownBy(() -> soilProfiles.deleteById(soil.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(soil.getId().toString())
                .hasMessageContaining("planos de manejo");
        assertThat(soilProfiles.findById(soil.getId())).isPresent();
    }

    @Test
    void deletingACropReferencedByAPlanIsRefusedAndItsStagesSurvive() {
        Crop crop = newCrop();
        plans.save(TestData.managementPlan(newArea().getId(), crop.getId(), newSoil().getId(), DAY));

        assertThatThrownBy(() -> crops.deleteById(crop.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(crop.getId().toString())
                .hasMessageContaining("planos de manejo");
        assertThat(crops.findById(crop.getId()).orElseThrow().getStages()).hasSize(crop.getStages().size());
    }

    @Test
    void unreferencedRegistrationsAreStillDeleted() {
        Area area = newArea();
        Crop crop = newCrop();
        SoilProfile soil = newSoil();

        areas.deleteById(area.getId());
        crops.deleteById(crop.getId());
        soilProfiles.deleteById(soil.getId());

        assertThat(areas.existsById(area.getId())).isFalse();
        assertThat(crops.existsById(crop.getId())).isFalse();
        assertThat(soilProfiles.findById(soil.getId())).isEmpty();
    }
}
