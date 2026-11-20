package com.irrigation.application.service;

import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.application.dto.DailyWeatherOutput;
import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWaterBalanceRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedade 15: consulta por período de meteorologia, Irrigação Real e balanços correntes.
 *
 * <p>Para conjuntos arbitrários de registros e períodos [from, to], a consulta devolve exatamente os
 * registros do período (inclusivo) em ordem cronológica crescente; a paginação (padrões página 0, tamanho 20,
 * máximo 100) devolve páginas disjuntas cuja concatenação é o resultado completo, com total correto. Período
 * invertido, paginação inválida (sem ajuste automático) e mais de 366 dias nas entradas diárias geram 400.
 *
 * <p><b>Validates: Requirements 5.9, 5.10, 6.10, 13.6, 14.1, 14.4, 14.8</b>
 */
class PeriodQueryPropertyTest {

    /** Base das datas geradas; todas ficam antes de {@link TestData#TODAY}, aceitas pelas entradas diárias. */
    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);
    private static final int SPAN = 150;

    /** Um registro por data: deslocamento em dias a partir de {@link #BASE}. */
    @Provide
    Arbitrary<Set<Integer>> offsets() {
        return Arbitraries.integers().between(0, SPAN - 1).set().ofMaxSize(SPAN);
    }

    /** Período [from, to] com from <= to, que pode passar das pontas do conjunto. */
    @Provide
    Arbitrary<int[]> periods() {
        return Combinators.combine(
                        Arbitraries.integers().between(-10, SPAN + 10),
                        Arbitraries.integers().between(0, SPAN + 20))
                .as((a, len) -> new int[]{a, a + len});
    }

    private static LocalDate day(int offset) {
        return BASE.plusDays(offset);
    }

    private static List<LocalDate> expectedDates(Set<Integer> offsets, int from, int to) {
        return offsets.stream().filter(o -> o >= from && o <= to).sorted().map(PeriodQueryPropertyTest::day).toList();
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    // ------------------------------------------------------------ Meteorologia

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void weatherPeriodReturnsExactlyTheRecordsInRangeInAscendingOrder(
            @ForAll("offsets") Set<Integer> offsets, @ForAll("periods") int[] period) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        DailyWeatherService service = new DailyWeatherService(
                new InMemoryDailyWeatherRepository(), areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();
        // Inserção em ordem embaralhada (conjunto de hash) para não depender da ordem de gravação.
        for (int o : new HashSet<>(offsets)) {
            service.upsert(areaId, TestData.dailyWeatherInput(day(o), "5", "1"));
        }

        List<DailyWeatherOutput> result = service.findByPeriod(areaId, day(period[0]), day(period[1]));

        assertThat(result).extracting(DailyWeatherOutput::date)
                .containsExactlyElementsOf(expectedDates(offsets, period[0], period[1]));
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void weatherInvalidPeriodIsRejectedWith400(
            @ForAll("offsets") Set<Integer> offsets,
            @ForAll("periods") int[] period,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 400) int gap,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 367, max = 800) int tooLong) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        DailyWeatherService service = new DailyWeatherService(
                new InMemoryDailyWeatherRepository(), areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();

        LocalDate from = day(period[0]);
        assertThatThrownBy(() -> service.findByPeriod(areaId, from.plusDays(gap), from))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("from"));
        assertThatThrownBy(() -> service.findByPeriod(areaId, from, from.plusDays(tooLong - 1)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("to"));
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void weatherPeriodOf366DaysIsAccepted(@ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 300) int start) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        DailyWeatherService service = new DailyWeatherService(
                new InMemoryDailyWeatherRepository(), areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();

        LocalDate from = day(start);
        assertThat(service.findByPeriod(areaId, from, from.plusDays(365))).isEmpty();
        assertThat(ChronoUnit.DAYS.between(from, from.plusDays(365)) + 1).isEqualTo(366);
    }

    // ------------------------------------------------------------ Irrigação Real

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void irrigationPeriodReturnsExactlyTheRecordsInRangeInAscendingOrder(
            @ForAll("offsets") Set<Integer> offsets, @ForAll("periods") int[] period) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        AppliedIrrigationService service = new AppliedIrrigationService(
                new InMemoryAppliedIrrigationRepository(), areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();
        for (int o : new HashSet<>(offsets)) {
            service.upsert(areaId, TestData.appliedIrrigationInput(day(o), "3"));
        }

        List<AppliedIrrigationOutput> result = service.findByPeriod(areaId, day(period[0]), day(period[1]));

        assertThat(result).extracting(AppliedIrrigationOutput::date)
                .containsExactlyElementsOf(expectedDates(offsets, period[0], period[1]));
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void irrigationInvalidPeriodIsRejectedWith400(
            @ForAll("periods") int[] period,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 400) int gap,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 367, max = 800) int tooLong) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        AppliedIrrigationService service = new AppliedIrrigationService(
                new InMemoryAppliedIrrigationRepository(), areas, TestData.fixedClock());
        UUID areaId = areas.save(TestData.area()).getId();

        LocalDate from = day(period[0]);
        assertThatThrownBy(() -> service.findByPeriod(areaId, from.plusDays(gap), from))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("from"));
        assertThatThrownBy(() -> service.findByPeriod(areaId, from, from.plusDays(tooLong - 1)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("to"));
    }

    // ------------------------------------------------------------ Balanços correntes

    private record BalanceFixture(WaterBalanceQueryService service, UUID areaId) {
    }

    /**
     * Grava, para cada deslocamento, o balanço corrente; para datas sorteadas grava também uma revisão
     * antiga (não corrente), que não pode aparecer na consulta.
     */
    private static BalanceFixture balances(Set<Integer> offsets, Set<Integer> withOldRevision) {
        InMemoryAreaRepository areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        InMemoryDailyWaterBalanceRepository repository = new InMemoryDailyWaterBalanceRepository();
        UUID areaId = areas.save(TestData.area()).getId();
        UUID planId = UUID.randomUUID();
        UUID cropId = UUID.randomUUID();
        UUID soilId = UUID.randomUUID();
        List<Integer> shuffled = new ArrayList<>(new HashSet<>(offsets));
        for (int o : shuffled) {
            if (withOldRevision.contains(o)) {
                repository.insert(TestData.dailyWaterBalance(areaId, cropId, soilId, planId, day(o), 1, false));
                repository.insert(TestData.dailyWaterBalance(areaId, cropId, soilId, planId, day(o), 2, true));
            } else {
                repository.insert(TestData.dailyWaterBalance(areaId, cropId, soilId, planId, day(o), 1, true));
            }
        }
        return new BalanceFixture(new WaterBalanceQueryService(repository, areas), areaId);
    }

    @Provide
    Arbitrary<Set<Integer>> oldRevisions() {
        return Arbitraries.integers().between(0, SPAN - 1).set().ofMaxSize(SPAN);
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void balancePagesAreDisjointAndConcatenateToTheFullCurrentResult(
            @ForAll("offsets") Set<Integer> offsets,
            @ForAll("oldRevisions") Set<Integer> oldRevisions,
            @ForAll("periods") int[] period,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 100) int size) {
        BalanceFixture f = balances(offsets, oldRevisions);
        List<LocalDate> expected = expectedDates(offsets, period[0], period[1]);

        List<LocalDate> collected = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        int page = 0;
        PagedResult<WaterBalanceOutput> result;
        do {
            result = f.service().findByPeriod(f.areaId(), day(period[0]), day(period[1]), page, size);
            assertThat(result.page()).isEqualTo(page);
            assertThat(result.size()).isEqualTo(size);
            assertThat(result.totalElements()).isEqualTo(expected.size());
            assertThat(result.content().size()).isLessThanOrEqualTo(size);
            for (WaterBalanceOutput b : result.content()) {
                assertThat(ids.add(b.id())).as("registro repetido entre páginas").isTrue();
                assertThat(b.control().current()).isTrue();
                collected.add(b.date());
            }
            page++;
        } while (page < result.totalPages());

        assertThat(collected).containsExactlyElementsOf(expected);
        assertThat(result.totalPages()).isEqualTo((expected.size() + size - 1) / size);
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void balanceDefaultsAreFirstPageWithSize20(
            @ForAll("offsets") Set<Integer> offsets, @ForAll("periods") int[] period) {
        BalanceFixture f = balances(offsets, Set.of());
        List<LocalDate> expected = expectedDates(offsets, period[0], period[1]);

        PagedResult<WaterBalanceOutput> result =
                f.service().findByPeriod(f.areaId(), day(period[0]), day(period[1]), null, null);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(expected.size());
        assertThat(result.content()).extracting(WaterBalanceOutput::date)
                .containsExactlyElementsOf(expected.subList(0, Math.min(20, expected.size())));
    }

    // Feature: irrigation-water-balance-module, Property 15: Consulta por período
    @Property(tries = 100)
    void balanceInvalidPeriodOrPaginationIsRejectedWith400WithoutAdjustment(
            @ForAll("offsets") Set<Integer> offsets,
            @ForAll("periods") int[] period,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 400) int gap,
            @ForAll @net.jqwik.api.constraints.IntRange(min = -500, max = -1) int negativePage,
            @ForAll("invalidSizes") int invalidSize) {
        BalanceFixture f = balances(offsets, Set.of());
        LocalDate from = day(period[0]);
        LocalDate to = day(period[1]);

        assertThatThrownBy(() -> f.service().findByPeriod(f.areaId(), to.plusDays(gap), to, 0, 20))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("from"));
        assertThatThrownBy(() -> f.service().findByPeriod(f.areaId(), from, to, negativePage, 20))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("page"));
        assertThatThrownBy(() -> f.service().findByPeriod(f.areaId(), from, to, 0, invalidSize))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(fields(e)).contains("size"));
    }

    @Provide
    Arbitrary<Integer> invalidSizes() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(-500, 0),
                Arbitraries.integers().between(101, 5000));
    }
}
