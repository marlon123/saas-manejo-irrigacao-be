package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateWaterBalanceInput;
import com.irrigation.application.dto.ReprocessWaterBalanceInput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.repository.AppliedIrrigationRepository;
import com.irrigation.domain.repository.AreaOperationLock;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.DailyWeatherRepository;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.infrastructure.persistence.repository.DailyWaterBalanceRepositoryAdapter;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concorrência e atomicidade do cálculo e do reprocessamento com o contexto Spring completo e o PostgreSQL
 * real (Req 12.5, 12.9, 13.4, 13.5, 13.8).
 *
 * <p>Usa um decorador do repositório do balanço, que delega ao adaptador real e permite (a) parar a
 * transação depois de adquirido o bloqueio da área, para forçar a sobreposição de forma determinística, e
 * (b) falhar no N-ésimo insert, com um erro qualquer ou com uma violação real de constraint do banco,
 * depois que {@code markNotCurrent} e os inserts anteriores já foram executados na mesma transação.
 */
class WaterBalanceConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int DAYS = 3;

    @Autowired
    WaterBalanceService service;
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
    @Autowired
    AreaOperationLock lock;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    FaultInjectingBalanceRepository faults;

    @AfterEach
    void resetFaults() {
        faults.reset();
    }

    // ------------------------------------------------------------------------------------
    // Decorador do repositório
    // ------------------------------------------------------------------------------------

    enum FailureMode {
        /** Lança uma exceção qualquer no N-ésimo insert. */
        THROW,
        /** No N-ésimo insert grava a revisão 1 de novo, violando uq_daily_water_balance_revision no banco. */
        DUPLICATE_REVISION
    }

    /** Delega ao adaptador real; instrumentado para sincronização e injeção de falha. */
    static class FaultInjectingBalanceRepository implements DailyWaterBalanceRepository {

        private final DailyWaterBalanceRepository delegate;

        final AtomicInteger insertAttempts = new AtomicInteger();
        final AtomicInteger completedInserts = new AtomicInteger();
        final AtomicInteger markNotCurrentCalls = new AtomicInteger();
        volatile int failAtInsert;
        volatile FailureMode mode = FailureMode.THROW;
        volatile Runnable beforeInsert;
        volatile Runnable beforeInsertAll;

        FaultInjectingBalanceRepository(DailyWaterBalanceRepository delegate) {
            this.delegate = delegate;
        }

        void reset() {
            insertAttempts.set(0);
            completedInserts.set(0);
            markNotCurrentCalls.set(0);
            failAtInsert = 0;
            mode = FailureMode.THROW;
            beforeInsert = null;
            beforeInsertAll = null;
        }

        void failAt(int n, FailureMode failureMode) {
            insertAttempts.set(0);
            completedInserts.set(0);
            markNotCurrentCalls.set(0);
            failAtInsert = n;
            mode = failureMode;
        }

        private DailyWaterBalance doInsert(DailyWaterBalance balance) {
            int attempt = insertAttempts.incrementAndGet();
            DailyWaterBalance toSave = balance;
            if (attempt == failAtInsert) {
                if (mode == FailureMode.THROW) {
                    throw new IllegalStateException("falha injetada no insert " + attempt);
                }
                toSave = new DailyWaterBalance(balance.id(), balance.areaId(), balance.date(), balance.cropId(),
                        balance.soilProfileId(), balance.managementPlanId(), balance.cycleDay(), balance.inputs(),
                        balance.result(), balance.calculatedAt(), balance.methodologyId(),
                        balance.methodologyVersion(), 1, balance.current());
            }
            DailyWaterBalance saved = delegate.insert(toSave);
            completedInserts.incrementAndGet();
            return saved;
        }

        @Override
        public DailyWaterBalance insert(DailyWaterBalance balance) {
            Runnable hook = beforeInsert;
            if (hook != null) {
                hook.run();
            }
            return doInsert(balance);
        }

        @Override
        public List<DailyWaterBalance> insertAll(List<DailyWaterBalance> balances) {
            Runnable hook = beforeInsertAll;
            if (hook != null) {
                hook.run();
            }
            List<DailyWaterBalance> saved = new ArrayList<>();
            for (DailyWaterBalance balance : balances) {
                saved.add(doInsert(balance));
            }
            return saved;
        }

        @Override
        public Optional<DailyWaterBalance> findCurrent(UUID areaId, LocalDate date) {
            return delegate.findCurrent(areaId, date);
        }

        @Override
        public List<DailyWaterBalance> findCurrentFrom(UUID areaId, LocalDate date) {
            return delegate.findCurrentFrom(areaId, date);
        }

        @Override
        public List<DailyWaterBalance> findCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to,
                                                           PageRequest pageRequest) {
            return delegate.findCurrentByPeriod(areaId, from, to, pageRequest);
        }

        @Override
        public long countCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to) {
            return delegate.countCurrentByPeriod(areaId, from, to);
        }

        @Override
        public List<DailyWaterBalance> findRevisions(UUID areaId, LocalDate date) {
            return delegate.findRevisions(areaId, date);
        }

        @Override
        public int markNotCurrent(UUID areaId, LocalDate fromDate) {
            markNotCurrentCalls.incrementAndGet();
            return delegate.markNotCurrent(areaId, fromDate);
        }

        @Override
        public Optional<LocalDate> firstMissingDate(UUID areaId, LocalDate fromDate, LocalDate toDate) {
            return delegate.firstMissingDate(areaId, fromDate, toDate);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfig {

        @Bean
        @Primary
        FaultInjectingBalanceRepository faultInjectingBalanceRepository(DailyWaterBalanceRepositoryAdapter adapter) {
            return new FaultInjectingBalanceRepository(adapter);
        }
    }

    // ------------------------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------------------------

    /** Área do Cenário A com meteorologia e Irrigação Real (inclusive zero) dos {@link #DAYS} primeiros dias. */
    private UUID newScenarioArea() {
        Area area = areas.save(TestData.area(UUID.randomUUID(), "Talhão " + UUID.randomUUID(), "10.50"));
        SoilProfile soil = soilProfiles.save(TestData.scenarioSoilProfile());
        Crop crop = crops.save(TestData.scenarioCrop());
        ManagementPlan plan = plans.save(TestData.scenarioPlan(area.getId(), crop.getId(), soil.getId()));
        assertThat(plan.getStartDate()).isEqualTo(TestData.scenarioDate(1));
        TestData.scenarioWeather(area.getId(), DAYS).forEach(weathers::upsert);
        for (int i = 0; i < DAYS; i++) {
            irrigations.upsert(TestData.appliedIrrigation(area.getId(), TestData.scenarioDate(i + 1),
                    TestData.SCENARIO_A_IRRIGATION.get(i)));
        }
        return area.getId();
    }

    private WaterBalanceOutput calculateDay(UUID areaId, int day) {
        return service.calculate(areaId, new CalculateWaterBalanceInput(TestData.scenarioDate(day),
                TestData.SCENARIO_A_CYCLE_DAYS.get(day - 1)));
    }

    private void calculateAllDays(UUID areaId) {
        for (int day = 1; day <= DAYS; day++) {
            calculateDay(areaId, day);
        }
    }

    private List<WaterBalanceOutput> reprocess(UUID areaId, int fromDay) {
        return service.reprocess(areaId, new ReprocessWaterBalanceInput(TestData.scenarioDate(fromDay)));
    }

    private int countWhere(UUID areaId, String condition) {
        return jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? AND " + condition,
                Integer.class, areaId);
    }

    /** Estado persistido da área: (data, revisão, corrente) em ordem, para comparar antes e depois. */
    private List<String> persistedState(UUID areaId) {
        return jdbc.queryForList("SELECT balance_date || '/' || revision || '/' || is_current || '/' || id "
                + "FROM daily_water_balance WHERE area_id = ? ORDER BY balance_date, revision", String.class, areaId);
    }

    private static <T> Future<Object> submit(ExecutorService pool, Callable<T> task) {
        return pool.submit(() -> {
            try {
                return task.call();
            } catch (Throwable t) {
                return t;
            }
        });
    }

    /** Duas tarefas ao mesmo tempo (largada sincronizada); devolve o resultado ou a exceção de cada uma. */
    private static <T> List<Object> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<T> task : List.of(first, second)) {
                futures.add(submit(pool, () -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------------------------
    // Bloqueio por área
    // ------------------------------------------------------------------------------------

    @Test
    void areaLockIsExclusivePerAreaAndReleasedWhenTheTransactionEnds() throws Exception {
        UUID areaId = UUID.randomUUID();
        UUID otherAreaId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                assertThat(lock.tryLock(areaId)).isTrue();
                assertThat(lock.tryLock(areaId)).as("mesma transação, reentrante").isTrue();
                try {
                    boolean sameArea = pool.submit(() -> tx.execute(s -> lock.tryLock(areaId)))
                            .get(30, TimeUnit.SECONDS);
                    boolean otherArea = pool.submit(() -> tx.execute(s -> lock.tryLock(otherAreaId)))
                            .get(30, TimeUnit.SECONDS);
                    assertThat(sameArea).as("outra transação, mesma área").isFalse();
                    assertThat(otherArea).as("outra transação, outra área").isTrue();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            boolean afterCommit = pool.submit(() -> tx.execute(s -> lock.tryLock(areaId))).get(30, TimeUnit.SECONDS);
            assertThat(afterCommit).as("liberado no commit").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void areaLockIsReleasedOnRollback() throws Exception {
        UUID areaId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            assertThat(lock.tryLock(areaId)).isTrue();
            throw new IllegalStateException("desfaz");
        })).isInstanceOf(IllegalStateException.class);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertThat(pool.submit(() -> tx.execute(s -> lock.tryLock(areaId))).get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------------------------
    // Dois cálculos concorrentes da mesma área e data
    // ------------------------------------------------------------------------------------

    @Test
    void twoRacingCalculationsOfTheSameAreaAndDateLeaveOneSuccessAndOneCurrentRow() throws Exception {
        for (int round = 0; round < 8; round++) {
            UUID areaId = newScenarioArea();

            List<Object> outcomes = race(() -> calculateDay(areaId, 1), () -> calculateDay(areaId, 1));

            assertThat(outcomes.stream().filter(WaterBalanceOutput.class::isInstance).count())
                    .as("rodada %d: cálculos bem-sucedidos (%s)", round, outcomes).isEqualTo(1);
            Object loser = outcomes.stream().filter(o -> !(o instanceof WaterBalanceOutput)).findFirst().orElseThrow();
            assertThat(loser).as("rodada %d: 409 pelo bloqueio ou 422 pela duplicidade", round)
                    .isInstanceOfAny(ConflictException.class, BusinessRuleException.class);
            assertThat(countWhere(areaId, "is_current")).as("rodada %d: linhas correntes", round).isEqualTo(1);
            assertThat(countWhere(areaId, "true")).as("rodada %d: linhas no total", round).isEqualTo(1);
        }
    }

    @Test
    void aCalculationStartedWhileAnotherHoldsTheAreaLockIsRefusedWith409() throws Exception {
        UUID areaId = newScenarioArea();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        faults.beforeInsert = () -> {
            entered.countDown();
            await(release);
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> first = submit(pool, () -> calculateDay(areaId, 1));
            assertThat(entered.await(30, TimeUnit.SECONDS)).as("primeiro cálculo parado com o bloqueio").isTrue();

            assertThatThrownBy(() -> calculateDay(areaId, 1)).isInstanceOf(ConflictException.class)
                    .hasMessageContaining(areaId.toString());
            assertThat(countWhere(areaId, "true")).as("nada gravado enquanto o primeiro não confirma").isZero();

            release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isInstanceOf(WaterBalanceOutput.class);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        faults.beforeInsert = null;

        assertThat(countWhere(areaId, "is_current")).isEqualTo(1);
        assertThat(countWhere(areaId, "true")).isEqualTo(1);
        assertThatThrownBy(() -> calculateDay(areaId, 1)).as("lock liberado; agora é duplicidade, 422")
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi calculada");
        // o cálculo de outra data da mesma área também segue normalmente depois do commit
        calculateDay(areaId, 2);
        assertThat(countWhere(areaId, "is_current")).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------
    // Reprocessamentos concorrentes da mesma área
    // ------------------------------------------------------------------------------------

    @Test
    void aReprocessStartedWhileAnotherHoldsTheAreaLockIsRefusedWith409() throws Exception {
        UUID areaId = newScenarioArea();
        calculateAllDays(areaId);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        faults.beforeInsertAll = () -> {
            // markNotCurrent já foi executado nesta transação; o bloqueio está retido
            entered.countDown();
            await(release);
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> first = submit(pool, () -> reprocess(areaId, 1));
            assertThat(entered.await(30, TimeUnit.SECONDS)).as("primeiro reprocesso parado com o bloqueio").isTrue();

            assertThatThrownBy(() -> reprocess(areaId, 1)).isInstanceOf(ConflictException.class)
                    .hasMessageContaining(areaId.toString());

            release.countDown();
            Object result = first.get(60, TimeUnit.SECONDS);
            assertThat(result).as("resultado do primeiro: %s", result).isInstanceOf(List.class);
            assertThat((List<?>) result).hasSize(DAYS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        faults.beforeInsertAll = null;

        // só uma nova revisão por data: o 409 não gravou nada
        assertThat(countWhere(areaId, "revision = 2 AND is_current")).isEqualTo(DAYS);
        assertThat(countWhere(areaId, "revision = 1 AND NOT is_current")).isEqualTo(DAYS);
        assertThat(countWhere(areaId, "revision > 2")).isZero();
        assertThat(countWhere(areaId, "true")).isEqualTo(2 * DAYS);
        // e o bloqueio foi liberado: um novo reprocesso gera a revisão 3
        reprocess(areaId, 1);
        assertThat(countWhere(areaId, "revision = 3 AND is_current")).isEqualTo(DAYS);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tempo esgotado esperando a liberação");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------
    // Rollback completo quando a gravação falha no meio do reprocessamento
    // ------------------------------------------------------------------------------------

    @Test
    void anErrorAtTheNthInsertRollsBackMarkNotCurrentAndEveryEarlierInsert() {
        for (int failAt = 1; failAt <= DAYS; failAt++) {
            UUID areaId = newScenarioArea();
            calculateAllDays(areaId);
            List<String> before = persistedState(areaId);
            assertThat(before).hasSize(DAYS);
            faults.failAt(failAt, FailureMode.THROW);
            int n = failAt;

            assertThatThrownBy(() -> reprocess(areaId, 1)).as("falha no insert %d", n)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("falha injetada");

            assertThat(faults.markNotCurrentCalls.get()).as("markNotCurrent foi executado antes da falha")
                    .isEqualTo(1);
            assertThat(faults.completedInserts.get()).as("inserts concluídos antes da falha").isEqualTo(n - 1);
            assertThat(persistedState(areaId)).as("estado idêntico ao anterior (falha no insert %d)", n)
                    .isEqualTo(before);
            assertThat(countWhere(areaId, "revision > 1")).as("nenhuma revisão nova").isZero();
            assertThat(countWhere(areaId, "is_current")).as("markNotCurrent desfeito").isEqualTo(DAYS);
            assertThat(countWhere(areaId, "NOT is_current")).isZero();
        }
    }

    @Test
    void aRealConstraintViolationAtTheNthInsertRollsBackEverythingAndTheNextAttemptSucceeds() {
        for (int failAt = 1; failAt <= DAYS; failAt++) {
            UUID areaId = newScenarioArea();
            calculateAllDays(areaId);
            List<String> before = persistedState(areaId);
            faults.failAt(failAt, FailureMode.DUPLICATE_REVISION);
            int n = failAt;

            assertThatThrownBy(() -> reprocess(areaId, 1)).as("violação no insert %d", n)
                    .isInstanceOf(ConflictException.class);

            assertThat(faults.markNotCurrentCalls.get()).isEqualTo(1);
            assertThat(faults.completedInserts.get()).isEqualTo(n - 1);
            assertThat(persistedState(areaId)).as("estado idêntico ao anterior (violação no insert %d)", n)
                    .isEqualTo(before);
            assertThat(countWhere(areaId, "is_current")).isEqualTo(DAYS);

            // com a falha desligada, o mesmo reprocessamento funciona e o bloqueio já foi liberado
            faults.reset();
            List<WaterBalanceOutput> outcome = reprocess(areaId, 1);
            assertThat(outcome).hasSize(DAYS).allSatisfy(o -> assertThat(o.control().revision()).isEqualTo(2));
            assertThat(countWhere(areaId, "revision = 2 AND is_current")).isEqualTo(DAYS);
            assertThat(countWhere(areaId, "revision = 1 AND NOT is_current")).isEqualTo(DAYS);
        }
    }

    @Test
    void aFailureReprocessingFromTheMiddleOfTheChainLeavesEarlierDatesUntouched() {
        UUID areaId = newScenarioArea();
        calculateAllDays(areaId);
        List<String> before = persistedState(areaId);
        faults.failAt(2, FailureMode.THROW);

        assertThatThrownBy(() -> reprocess(areaId, 2)).isInstanceOf(IllegalStateException.class);

        assertThat(persistedState(areaId)).isEqualTo(before);
        assertThat(countWhere(areaId, "is_current")).isEqualTo(DAYS);
    }
}
