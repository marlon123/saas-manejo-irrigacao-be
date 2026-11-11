package com.irrigation.application;

import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.dto.CalculateRecommendationInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.RecommendationOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.service.AreaService;
import com.irrigation.application.service.RecommendationService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.calculation.CalculationEngine;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.support.FakeCalculationMethod;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Propriedades de correção 9 a 13 do design (histórico de recomendações, integridade
 * referencial, paginação e ordenação), exercitadas nos serviços com repositórios em memória.
 */
@PropertyDefaults(tries = 200)
class ServicePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime BASE = LocalDateTime.of(2025, 1, 1, 0, 0);
    private static final UUID[] AREA_IDS = {
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003")};

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // =====================================================================================
    // Propriedade 9: recomendação é um snapshot completo e imutável
    // =====================================================================================

    @Provide
    Arbitrary<BigDecimal> etoValues() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, bd("15")).ofScale(2);
    }

    // Feature: irrigation-management-backend, Property 9: snapshot completo e imutável
    @Property
    void recommendationIsACompleteSnapshotThatLaterMethodologiesDoNotChange(@ForAll("etoValues") BigDecimal eto) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        Area area = areas.save(TestData.area());
        CalculationEngine engine = new CalculationEngine();
        engine.register(new FakeCalculationMethod("m1", "1.0", new CalculationResult(bd("10"), bd("100"), bd("1"))));
        RecommendationService service = new RecommendationService(engine, recommendations, areas, CLOCK);

        CalculationParameters base = TestData.calculationParameters();
        CalculationParameters params = new CalculationParameters(eto, base.kc(), base.etc(),
                base.effectivePrecipitation(), base.currentSoilMoisture(), base.waterStorageCapacity(),
                base.irrigationEfficiency(), base.netIrrigationDepth(), base.grossIrrigationDepth(),
                base.irrigatedArea(), base.systemFlowRate(), base.irrigationTime());

        RecommendationOutput first = service.calculate(new CalculateRecommendationInput(area.getId(), "m1", params));

        // snapshot completo (Req 5.1)
        assertThat(first.id()).isNotNull();
        assertThat(first.areaId()).isEqualTo(area.getId());
        assertThat(first.dateTime()).isEqualTo(LocalDateTime.of(2025, 3, 1, 12, 0));
        assertThat(first.inputs()).isEqualTo(params);
        assertThat(first.results().irrigationDepth()).isEqualByComparingTo("10");
        assertThat(first.methodologyId()).isEqualTo("m1");
        assertThat(first.methodologyVersion()).isEqualTo("1.0");

        // a metodologia evolui: nova versão registrada e usada em outro cálculo (Req 5.7)
        engine.register(new FakeCalculationMethod("m2", "2.0", new CalculationResult(bd("77"), bd("700"), bd("7"))));
        RecommendationOutput second = service.calculate(new CalculateRecommendationInput(area.getId(), "m2", params));

        assertThat(second.methodologyVersion()).isEqualTo("2.0");
        assertThat(service.getById(first.id())).isEqualTo(first);
    }

    // =====================================================================================
    // Propriedade 10: filtros retornam apenas registros correspondentes
    // =====================================================================================

    record Scenario(List<int[]> recommendations, Integer area, Integer from, Integer to) {
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<List<int[]>> stored = Combinators
                .combine(Arbitraries.integers().between(0, 2), Arbitraries.integers().between(0, 1000))
                .as((area, minutes) -> new int[] {area, minutes})
                .list().ofMaxSize(15);
        return Combinators.combine(
                        stored,
                        Arbitraries.integers().between(0, 2).injectNull(0.3),
                        Arbitraries.integers().between(0, 1000).injectNull(0.4),
                        Arbitraries.integers().between(0, 1000).injectNull(0.4))
                .as(Scenario::new);
    }

    // Feature: irrigation-management-backend, Property 10: filtros por área e período
    @Property
    void filtersReturnOnlyMatchingRecommendationsOrAnEmptyPage(@ForAll("scenarios") Scenario scenario) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        RecommendationService service = new RecommendationService(new CalculationEngine(), recommendations, areas, CLOCK);
        Set<UUID> allIds = new HashSet<>();
        for (int[] r : scenario.recommendations()) {
            Recommendation saved = recommendations.save(
                    TestData.recommendation(AREA_IDS[r[0]], BASE.plusMinutes(r[1])));
            allIds.add(saved.getId());
        }

        UUID areaFilter = scenario.area() == null ? null : AREA_IDS[scenario.area()];
        LocalDateTime from = scenario.from() == null ? null : BASE.plusMinutes(scenario.from());
        LocalDateTime to = scenario.to() == null ? null : BASE.plusMinutes(scenario.to());

        if (from != null && to != null && from.isAfter(to)) {
            assertThatThrownBy(() -> service.list(areaFilter, from, to, null, 100))
                    .isInstanceOf(ValidationFailedException.class);
            return;
        }

        PagedResult<RecommendationOutput> result = service.list(areaFilter, from, to, null, 100);

        int expectedCount = 0;
        for (int[] r : scenario.recommendations()) {
            LocalDateTime at = BASE.plusMinutes(r[1]);
            boolean matches = (areaFilter == null || AREA_IDS[r[0]].equals(areaFilter))
                    && (from == null || !at.isBefore(from))
                    && (to == null || !at.isAfter(to));
            if (matches) {
                expectedCount++;
            }
        }
        assertThat(result.totalElements()).isEqualTo(expectedCount);
        assertThat(result.content()).hasSize(expectedCount);
        for (RecommendationOutput out : result.content()) {
            assertThat(allIds).contains(out.id());
            if (areaFilter != null) {
                assertThat(out.areaId()).isEqualTo(areaFilter);
            }
            if (from != null) {
                assertThat(out.dateTime()).isAfterOrEqualTo(from);
            }
            if (to != null) {
                assertThat(out.dateTime()).isBeforeOrEqualTo(to);
            }
        }
        if (expectedCount == 0) {
            assertThat(result.content()).isEmpty();
            assertThat(result.totalPages()).isZero();
        }
    }

    // =====================================================================================
    // Propriedade 11: área referenciada por recomendação não pode ser excluída
    // =====================================================================================

    // Feature: irrigation-management-backend, Property 11: integridade referencial na exclusão
    @Property
    void anAreaReferencedByARecommendationCannotBeDeleted(@ForAll boolean referenced) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        AreaService service = new AreaService(areas);
        Area area = areas.save(TestData.area());
        if (referenced) {
            recommendations.save(TestData.recommendation(area.getId(), BASE));
        }

        if (referenced) {
            assertThatThrownBy(() -> service.delete(area.getId())).isInstanceOf(BusinessRuleException.class);
            assertThat(areas.existsById(area.getId())).isTrue();
            assertThat(recommendations.count()).isEqualTo(1);
        } else {
            service.delete(area.getId());
            assertThat(areas.existsById(area.getId())).isFalse();
        }
    }

    // =====================================================================================
    // Propriedade 12: metadados de paginação consistentes
    // =====================================================================================

    // Feature: irrigation-management-backend, Property 12: totalPages = teto(total / tamanho)
    @Property
    void totalPagesIsTheCeilingOfTotalOverSize(
            @ForAll @LongRange(min = 0, max = 100_000) long total, @ForAll @IntRange(min = 1, max = 100) int size) {
        PagedResult<String> page = PagedResult.of(List.of(), 0, size, total);
        assertThat(page.totalPages()).isEqualTo((int) Math.ceil((double) total / size));
        assertThat(page.totalElements()).isEqualTo(total);
    }

    // Feature: irrigation-management-backend, Property 12: parâmetros de página válidos
    @Property
    void paginationParametersAreAcceptedExactlyWhenValid(
            @ForAll @IntRange(min = -5, max = 150) int page, @ForAll @IntRange(min = -5, max = 150) int size) {
        boolean pageValid = page >= 0;
        boolean sizeValid = size >= 1 && size <= 100;

        if (pageValid && sizeValid) {
            assertThat(PageParams.resolve(page, size).offset()).isEqualTo((long) page * size);
        } else {
            assertThatThrownBy(() -> PageParams.resolve(page, size))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e -> {
                        List<String> fields = e.getViolations().stream().map(FieldViolation::field).toList();
                        assertThat(fields.contains("page")).isEqualTo(!pageValid);
                        assertThat(fields.contains("size")).isEqualTo(!sizeValid);
                    });
        }
    }

    // Feature: irrigation-management-backend, Property 12: página além do fim retorna conteúdo vazio
    @Property
    void aPageBeyondTheLastOneIsEmptyWithConsistentMetadata(
            @ForAll @IntRange(min = 0, max = 30) int areaCount,
            @ForAll @IntRange(min = 0, max = 12) int page,
            @ForAll @IntRange(min = 1, max = 10) int size) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        for (int i = 0; i < areaCount; i++) {
            areas.save(TestData.area(UUID.randomUUID(), "Área " + i, "1.00"));
        }
        AreaService service = new AreaService(areas);

        var result = service.list(page, size, null, null);

        int expectedContent = Math.max(0, Math.min(size, areaCount - page * size));
        assertThat(result.content()).hasSize(expectedContent);
        assertThat(result.totalElements()).isEqualTo(areaCount);
        assertThat(result.totalPages()).isEqualTo((int) Math.ceil((double) areaCount / size));
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.size()).isEqualTo(size);
    }

    // =====================================================================================
    // Propriedade 13: ordenação de áreas restrita a campos válidos
    // =====================================================================================

    @Provide
    Arbitrary<String> sortFields() {
        return Arbitraries.oneOf(
                Arbitraries.of("name", "areaHectares"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12));
    }

    @Provide
    Arbitrary<List<BigDecimal>> hectares() {
        return Arbitraries.bigDecimals().between(bd("0.01"), bd("1000")).ofScale(2).list().ofMinSize(2).ofMaxSize(8);
    }

    // Feature: irrigation-management-backend, Property 13: ordenação válida ordena, inválida é rejeitada
    @Property
    void sortingIsLimitedToValidFieldsAndOrdersTheResults(
            @ForAll("sortFields") String sortBy, @ForAll boolean descending, @ForAll("hectares") List<BigDecimal> sizes) {
        InMemoryRecommendationRepository recommendations = new InMemoryRecommendationRepository();
        InMemoryAreaRepository areas = new InMemoryAreaRepository(recommendations);
        for (int i = 0; i < sizes.size(); i++) {
            areas.save(TestData.area(UUID.randomUUID(), "Área " + (char) ('A' + i), sizes.get(i).toPlainString()));
        }
        AreaService service = new AreaService(areas);
        String direction = descending ? "desc" : "asc";

        if (sortBy.equals("name") || sortBy.equals("areaHectares")) {
            var result = service.list(null, 100, sortBy, direction);
            Comparator<AreaOutput> comparator = sortBy.equals("name")
                    ? Comparator.comparing(AreaOutput::name)
                    : Comparator.comparing(AreaOutput::areaHectares);
            if (descending) {
                comparator = comparator.reversed();
            }
            assertThat(result.content()).isSortedAccordingTo(comparator);
        } else {
            assertThatThrownBy(() -> service.list(null, 100, sortBy, direction))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.getViolations()).extracting(FieldViolation::field).contains("sort"));
        }
    }
}
