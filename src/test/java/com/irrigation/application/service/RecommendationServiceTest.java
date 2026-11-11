package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateRecommendationInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.RecommendationOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.calculation.CalculationEngine;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.support.FakeCalculationMethod;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 3, 1, 12, 0);

    @Mock
    RecommendationRepository recommendations;

    @Mock
    AreaRepository areas;

    private CalculationEngine engine;
    private FakeCalculationMethod method;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        engine = new CalculationEngine();
        method = new FakeCalculationMethod("fake", "2.1", TestData.calculationResult());
        engine.register(method);
        Clock clock = Clock.fixed(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC);
        service = new RecommendationService(engine, recommendations, areas, clock);
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    // --------------------------------------------------------------- calculate

    @Test
    void calculateStoresASnapshotWithMethodologyAndVersion() {
        UUID areaId = UUID.randomUUID();
        when(areas.existsById(areaId)).thenReturn(true);
        when(recommendations.save(any(Recommendation.class))).thenAnswer(i -> i.getArgument(0));

        RecommendationOutput output = service.calculate(
                new CalculateRecommendationInput(areaId, "fake", TestData.calculationParameters()));

        assertThat(output.areaId()).isEqualTo(areaId);
        assertThat(output.dateTime()).isEqualTo(NOW);
        assertThat(output.methodologyId()).isEqualTo("fake");
        assertThat(output.methodologyVersion()).isEqualTo("2.1");
        assertThat(output.inputs()).isEqualTo(TestData.calculationParameters());
        assertThat(output.results().irrigationDepth()).isEqualByComparingTo("20");
        assertThat(method.calls()).isEqualTo(1);
    }

    @Test
    void calculateFailsWithNotFoundForAnUnknownAreaWithoutRunningTheMethod() {
        UUID areaId = UUID.randomUUID();
        when(areas.existsById(areaId)).thenReturn(false);

        assertThatThrownBy(() -> service.calculate(
                new CalculateRecommendationInput(areaId, "fake", TestData.calculationParameters())))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(method.calls()).isZero();
        verify(recommendations, never()).save(any());
    }

    @Test
    void calculateFailsForAnUnregisteredMethodology() {
        UUID areaId = UUID.randomUUID();
        when(areas.existsById(areaId)).thenReturn(true);

        assertThatThrownBy(() -> service.calculate(
                new CalculateRecommendationInput(areaId, "inexistente", TestData.calculationParameters())))
                .isInstanceOf(MethodologyNotAvailableException.class);
        verify(recommendations, never()).save(any());
    }

    @Test
    void calculateFailsWhenParametersAreMissingOrOutOfRange() {
        UUID areaId = UUID.randomUUID();
        when(areas.existsById(areaId)).thenReturn(true);
        CalculationParameters valid = TestData.calculationParameters();
        CalculationParameters invalid = new CalculationParameters(bd("99"), null, valid.etc(),
                valid.effectivePrecipitation(), valid.currentSoilMoisture(), valid.waterStorageCapacity(),
                valid.irrigationEfficiency(), valid.netIrrigationDepth(), valid.grossIrrigationDepth(),
                valid.irrigatedArea(), valid.systemFlowRate(), valid.irrigationTime());

        assertThatThrownBy(() -> service.calculate(new CalculateRecommendationInput(areaId, "fake", invalid)))
                .isInstanceOfSatisfying(InvalidCalculationInputException.class, e ->
                        assertThat(e.getInvalidParameters()).containsExactlyInAnyOrder("ETo", "Kc"));
        assertThat(method.calls()).isZero();
        verify(recommendations, never()).save(any());
    }

    @Test
    void calculateReportsValueObjectViolationsByField() {
        CalculationParameters valid = TestData.calculationParameters();
        CalculationParameters badKc = new CalculationParameters(valid.eto(), bd("5"), valid.etc(),
                valid.effectivePrecipitation(), valid.currentSoilMoisture(), valid.waterStorageCapacity(),
                bd("2"), valid.netIrrigationDepth(), valid.grossIrrigationDepth(), valid.irrigatedArea(),
                valid.systemFlowRate(), valid.irrigationTime());

        assertThatThrownBy(() -> service.calculate(
                new CalculateRecommendationInput(UUID.randomUUID(), "fake", badKc)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("kc", "irrigationEfficiency"));
    }

    @Test
    void calculateRequiresAreaMethodologyAndParameters() {
        assertThatThrownBy(() -> service.calculate(new CalculateRecommendationInput(null, null, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("areaId", "methodologyId", "parameters"));
    }

    // ----------------------------------------------------------------- getById

    @Test
    void getByIdReturnsOrFails() {
        Recommendation stored = TestData.recommendation(UUID.randomUUID(), NOW);
        when(recommendations.findById(stored.getId())).thenReturn(Optional.of(stored));
        UUID unknown = UUID.randomUUID();
        when(recommendations.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.getById(stored.getId()).id()).isEqualTo(stored.getId());
        assertThatThrownBy(() -> service.getById(unknown)).isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------------- list

    @Test
    void listByAreaOnlyUsesTheAreaQuery() {
        UUID areaId = UUID.randomUUID();
        when(recommendations.findByArea(any(), any())).thenReturn(List.of(TestData.recommendation(areaId, NOW)));
        when(recommendations.countByArea(areaId)).thenReturn(1L);

        var result = service.list(areaId, null, null, null, null);

        verify(recommendations).findByArea(areaId, PageRequest.of(0, 20));
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void listByPeriodOnlyUsesTheDateRangeQuery() {
        LocalDateTime from = NOW.minusDays(7);
        when(recommendations.findByDateRange(any(), any(), any())).thenReturn(List.of());
        when(recommendations.countByDateRange(any(), any())).thenReturn(0L);

        var result = service.list(null, from, NOW, 1, 10);

        verify(recommendations).findByDateRange(from, NOW, PageRequest.of(1, 10));
        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listByAreaAndPeriodUsesTheCombinedQuery() {
        UUID areaId = UUID.randomUUID();
        LocalDateTime from = NOW.minusDays(1);
        when(recommendations.findByAreaAndDateRange(any(), any(), any(), any())).thenReturn(List.of());
        when(recommendations.countByAreaAndDateRange(any(), any(), any())).thenReturn(0L);

        service.list(areaId, from, NOW, null, null);

        verify(recommendations).findByAreaAndDateRange(areaId, from, NOW, PageRequest.of(0, 20));
    }

    @Test
    void listWithAnOpenEndedPeriodUsesTheSentinelBounds() {
        LocalDateTime from = NOW.minusDays(1);
        when(recommendations.findByDateRange(any(), any(), any())).thenReturn(List.of());
        when(recommendations.countByDateRange(any(), any())).thenReturn(0L);

        service.list(null, from, null, null, null);

        verify(recommendations).findByDateRange(from, RecommendationService.MAX_DATE_TIME, PageRequest.of(0, 20));
    }

    @Test
    void listWithoutFiltersReturnsEverything() {
        when(recommendations.findByDateRange(any(), any(), any())).thenReturn(List.of());
        when(recommendations.countByDateRange(any(), any())).thenReturn(0L);

        service.list(null, null, null, null, null);

        verify(recommendations).findByDateRange(
                RecommendationService.MIN_DATE_TIME, RecommendationService.MAX_DATE_TIME, PageRequest.of(0, 20));
    }

    @Test
    void listRejectsAnInvertedPeriodAndInvalidPagination() {
        assertThatThrownBy(() -> service.list(null, NOW, NOW.minusDays(1), null, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("dateFrom"));
        assertThatThrownBy(() -> service.list(null, null, null, -1, 500))
                .isInstanceOf(ValidationFailedException.class);
    }
}
