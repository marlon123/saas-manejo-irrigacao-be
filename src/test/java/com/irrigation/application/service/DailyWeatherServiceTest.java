package com.irrigation.application.service;

import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.DailyWeatherOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryDailyWeatherRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.TODAY;
import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes unitários do {@link DailyWeatherService} (Req 5, 11.6), sem Spring nem Docker. */
class DailyWeatherServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private InMemoryDailyWeatherRepository repository;
    private InMemoryAreaRepository areas;
    private DailyWeatherService service;
    private UUID areaId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDailyWeatherRepository();
        areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        service = new DailyWeatherService(repository, areas, TestData.fixedClock());
        areaId = areas.save(TestData.area()).getId();
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    @Test
    void upsertCreatesTheRecord() {
        DailyWeatherOutput out = service.upsert(areaId, TestData.dailyWeatherInput(DAY, "5.20", "0"));

        assertThat(out.areaId()).isEqualTo(areaId);
        assertThat(out.date()).isEqualTo(DAY);
        assertThat(out.etpMm()).isEqualByComparingTo("5.20");
        assertThat(out.precipitationMm()).isEqualByComparingTo("0");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void upsertReplacesValuesKeepingASingleRow() {
        DailyWeatherOutput first = service.upsert(areaId, TestData.dailyWeatherInput(DAY, "5", "1"));

        DailyWeatherOutput second = service.upsert(areaId, TestData.dailyWeatherInput(DAY, "6.5", "2.5"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.etpMm()).isEqualByComparingTo("6.5");
        assertThat(second.precipitationMm()).isEqualByComparingTo("2.5");
        assertThat(service.findByDate(areaId, DAY).etpMm()).isEqualByComparingTo("6.5");
    }

    @Test
    void zeroPrecipitationAndZeroEtpAreAccepted() {
        DailyWeatherOutput out = service.upsert(areaId, TestData.dailyWeatherInput(DAY, "0", "0"));

        assertThat(out.etpMm()).isEqualByComparingTo("0");
    }

    @Test
    void omittedEtpIsRejectedWithoutAssumingZero() {
        assertThatThrownBy(() -> service.upsert(areaId, new DailyWeatherInput(DAY, null, bd("1"))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("etpMm"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void omittedPrecipitationIsRejectedWithoutAssumingZero() {
        assertThatThrownBy(() -> service.upsert(areaId, new DailyWeatherInput(DAY, bd("1"), null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("precipitationMm"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void omittedPrecipitationDoesNotOverwriteAnExistingRecord() {
        service.upsert(areaId, TestData.dailyWeatherInput(DAY, "5", "3"));

        assertThatThrownBy(() -> service.upsert(areaId, new DailyWeatherInput(DAY, bd("9"), null)))
                .isInstanceOf(ValidationFailedException.class);

        DailyWeatherOutput stored = service.findByDate(areaId, DAY);
        assertThat(stored.etpMm()).isEqualByComparingTo("5");
        assertThat(stored.precipitationMm()).isEqualByComparingTo("3");
    }

    @Test
    void allMissingFieldsAreReportedTogether() {
        assertThatThrownBy(() -> service.upsert(areaId, new DailyWeatherInput(null, null, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("date", "etpMm", "precipitationMm"));
    }

    @Test
    void nullBodyReportsAllFields() {
        assertThatThrownBy(() -> service.upsert(areaId, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("date", "etpMm", "precipitationMm"));
    }

    @Test
    void negativeAndOutOfRangeValuesAreRejected() {
        assertThatThrownBy(() -> service.upsert(areaId, TestData.dailyWeatherInput(DAY, "-0.01", "1001")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("etpMm", "precipitationMm"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void futureDateIsRejectedAndTodayIsAccepted() {
        assertThatThrownBy(() -> service.upsert(areaId, TestData.dailyWeatherInput(TODAY.plusDays(1), "5", "0")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("date"));
        assertThat(repository.count()).isZero();

        service.upsert(areaId, TestData.dailyWeatherInput(TODAY, "5", "0"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void validationErrorWinsOverMissingArea() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), new DailyWeatherInput(DAY, null, bd("1"))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void upsertForUnknownAreaIs404AndPersistsNothing() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), TestData.dailyWeatherInput(DAY, "5", "0")))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(repository.count()).isZero();
    }

    // -------------------------------------------------------------- consulta

    @Test
    void findByPeriodReturnsInclusiveRangeInAscendingOrder() {
        service.upsert(areaId, TestData.dailyWeatherInput(DAY.plusDays(2), "3", "0"));
        service.upsert(areaId, TestData.dailyWeatherInput(DAY, "1", "0"));
        service.upsert(areaId, TestData.dailyWeatherInput(DAY.plusDays(1), "2", "0"));
        service.upsert(areaId, TestData.dailyWeatherInput(DAY.plusDays(5), "9", "0"));

        List<DailyWeatherOutput> result = service.findByPeriod(areaId, DAY, DAY.plusDays(2));

        assertThat(result).extracting(DailyWeatherOutput::date)
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
    void findByPeriodWithMissingBoundsIs400() {
        assertThatThrownBy(() -> service.findByPeriod(areaId, null, null))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("from", "to"));
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
