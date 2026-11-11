package com.irrigation.application.service;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SoilProfileServiceTest {

    @Mock
    SoilProfileRepository repository;

    @InjectMocks
    SoilProfileService service;

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    @Test
    void createPersistsTheProfileWithItsUnits() {
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.create(TestData.soilProfileInput());

        assertThat(output.soilType()).isEqualTo("Argiloso");
        assertThat(output.bulkDensityUnit()).isEqualTo(MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER);
        assertThat(output.effectiveRootDepth()).isEqualByComparingTo("600");
    }

    @Test
    void createReportsFieldCapacityNotGreaterThanWiltingPoint() {
        SoilProfileInput in = new SoilProfileInput("Argiloso", bd("0.20"), bd("0.30"), bd("1.3"), bd("500"),
                bd("90"), MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("fieldCapacity"));
        verify(repository, never()).save(any());
    }

    @Test
    void createReportsEveryInvalidFieldAtOnce() {
        SoilProfileInput in = new SoilProfileInput(" ", bd("1.5"), bd("0.1"), bd("0"), bd("5000.01"),
                bd("-1"), MeasurementUnit.PERCENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "soilType", "fieldCapacity", "bulkDensity", "effectiveRootDepth", "availableWater"));
    }

    @Test
    void createRequiresEveryUnit() {
        SoilProfileInput in = new SoilProfileInput("Argiloso", bd("0.35"), bd("0.18"), bd("1.3"), bd("500"),
                bd("90"), null, null, null, null);

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "soilWaterUnit", "bulkDensityUnit", "rootDepthUnit", "availableWaterUnit"));
    }

    @Test
    void updateReplacesTheProfile() {
        SoilProfile existing = TestData.soilProfile();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.update(existing.getId(), new SoilProfileInput("Arenoso", bd("0.30"),
                bd("0.10"), bd("1.5"), bd("400"), bd("70"), MeasurementUnit.PERCENT,
                MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER, MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.soilType()).isEqualTo("Arenoso");
        assertThat(output.soilWaterUnit()).isEqualTo(MeasurementUnit.PERCENT);
    }

    @Test
    void updateFailsWithNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, TestData.soilProfileInput()))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesOrFailsWithNotFound() {
        SoilProfile existing = TestData.soilProfile();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        service.delete(existing.getId());

        verify(repository).deleteById(existing.getId());
        assertThatThrownBy(() -> service.delete(unknown)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).deleteById(unknown);
    }

    @Test
    void getByIdReturnsOrFails() {
        SoilProfile existing = TestData.soilProfile();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.getById(existing.getId()).soilType()).isEqualTo("Argiloso");
        assertThatThrownBy(() -> service.getById(unknown)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listUsesDefaultsAndRejectsInvalidPagination() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(List.of(TestData.soilProfile()));
        when(repository.count()).thenReturn(1L);

        assertThat(service.list(null, null).content()).hasSize(1);
        verify(repository).findAll(PageRequest.of(0, 20));
        assertThatThrownBy(() -> service.list(-1, 20)).isInstanceOf(ValidationFailedException.class);
    }
}
