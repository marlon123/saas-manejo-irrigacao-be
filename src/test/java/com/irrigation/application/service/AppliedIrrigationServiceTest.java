package com.irrigation.application.service;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.support.InMemoryAppliedIrrigationRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes unitários do {@link AppliedIrrigationService} (Req 6, 11.6), sem Spring nem Docker. */
class AppliedIrrigationServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private InMemoryAppliedIrrigationRepository repository;
    private InMemoryAreaRepository areas;
    private AppliedIrrigationService service;
    private UUID areaId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAppliedIrrigationRepository();
        areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        service = new AppliedIrrigationService(repository, areas, TestData.fixedClock());
        areaId = areas.save(TestData.area()).getId();
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    @Test
    void upsertCreatesTheRecord() {
        AppliedIrrigationOutput out = service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "12.50"));

        assertThat(out.areaId()).isEqualTo(areaId);
        assertThat(out.date()).isEqualTo(DAY);
        assertThat(out.appliedIrrigationMm()).isEqualByComparingTo("12.5");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void upsertReplacesTheValueKeepingASingleRow() {
        AppliedIrrigationOutput first = service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "10"));

        AppliedIrrigationOutput second = service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "7.25"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.findByDate(areaId, DAY).appliedIrrigationMm()).isEqualByComparingTo("7.25");
    }

    @Test
    void explicitZeroIsAccepted() {
        AppliedIrrigationOutput out = service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "0"));

        assertThat(out.appliedIrrigationMm()).isEqualByComparingTo("0");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void zeroOverwritesAPreviousPositiveValue() {
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "10"));

        service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "0"));

        assertThat(service.findByDate(areaId, DAY).appliedIrrigationMm()).isEqualByComparingTo("0");
    }

    @Test
    void omittedIrrigationIsRejectedWithoutAssumingZero() {
        assertThatThrownBy(() -> service.upsert(areaId, new AppliedIrrigationInput(DAY, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("appliedIrrigationMm"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void omittedIrrigationDoesNotOverwriteAnExistingRecord() {
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "10"));

        assertThatThrownBy(() -> service.upsert(areaId, new AppliedIrrigationInput(DAY, null)))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(service.findByDate(areaId, DAY).appliedIrrigationMm()).isEqualByComparingTo("10");
    }

    @Test
    void missingDateAndIrrigationAreReportedTogether() {
        assertThatThrownBy(() -> service.upsert(areaId, new AppliedIrrigationInput(null, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("date", "appliedIrrigationMm"));
        assertThatThrownBy(() -> service.upsert(areaId, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("date", "appliedIrrigationMm"));
    }

    @Test
    void negativeAndOutOfRangeValuesAreRejected() {
        assertThatThrownBy(() -> service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "-1")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("appliedIrrigationMm"));
        assertThatThrownBy(() -> service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "1000.01")))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void futureDateIsRejectedAndTodayIsAccepted() {
        assertThatThrownBy(() -> service.upsert(areaId, TestData.appliedIrrigationInput(TODAY.plusDays(1), "5")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("date"));
        assertThat(repository.count()).isZero();

        service.upsert(areaId, TestData.appliedIrrigationInput(TODAY, "5"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void validationErrorWinsOverMissingArea() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), new AppliedIrrigationInput(DAY, null)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void upsertForUnknownAreaIs404AndPersistsNothing() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), TestData.appliedIrrigationInput(DAY, "5")))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(repository.count()).isZero();
    }

    // -------------------------------------------------------------- consulta

    @Test
    void findByPeriodReturnsInclusiveRangeInAscendingOrder() {
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY.plusDays(2), "3"));
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY, "1"));
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY.plusDays(1), "2"));
        service.upsert(areaId, TestData.appliedIrrigationInput(DAY.plusDays(5), "9"));

        assertThat(service.findByPeriod(areaId, DAY, DAY.plusDays(2))).extracting(AppliedIrrigationOutput::date)
                .containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2));
    }

    @Test
    void findByPeriodWithoutRecordsIsAnEmptyList() {
        assertThat(service.findByPeriod(areaId, DAY, DAY.plusDays(10))).isEmpty();
    }

    @Test
    void findByPeriodOfUnknownAreaIs404() {
        assertThatThrownBy(() -> service.findByPeriod(UUID.randomUUID(), DAY, DAY.plusDays(1)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findByPeriodWithFromAfterToIs400() {
        assertThatThrownBy(() -> service.findByPeriod(areaId, DAY.plusDays(1), DAY))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("from"));
    }

    @Test
    void findByPeriodLongerThan366DaysIs400AndExactly366IsAccepted() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> service.findByPeriod(areaId, from, from.plusDays(366)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("to"));
        assertThat(service.findByPeriod(areaId, from, from.plusDays(365))).isEmpty();
    }

    @Test
    void periodErrorWinsOverMissingArea() {
        assertThatThrownBy(() -> service.findByPeriod(UUID.randomUUID(), DAY.plusDays(1), DAY))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void findByDateReturns404WithoutRecordAndForUnknownArea() {
        assertThatThrownBy(() -> service.findByDate(areaId, DAY)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.findByDate(UUID.randomUUID(), DAY))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
