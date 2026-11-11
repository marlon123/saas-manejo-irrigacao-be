package com.irrigation.application.service;

import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
class CropServiceTest {

    @Mock
    CropRepository repository;

    @InjectMocks
    CropService service;

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    private static CropInput crop(String name, List<CropStageInput> stages) {
        return new CropInput(name, null, 100, stages);
    }

    @Test
    void createPersistsTheCropWithItsStages() {
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));

        CropOutput output = service.create(TestData.cropInput());

        assertThat(output.name()).isEqualTo("Milho");
        assertThat(output.stages()).extracting("stageOrder").containsExactly(1, 2);
        assertThat(output.stages().get(1).kc()).isEqualByComparingTo("1.15");
    }

    @Test
    void createRejectsAnEmptyStageList() {
        assertThatThrownBy(() -> service.create(crop("Milho", List.of())))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("stages"));
        verify(repository, never()).save(any());
    }

    @Test
    void createReportsInvalidStageFieldsWithTheirPosition() {
        List<CropStageInput> stages = List.of(
                new CropStageInput("Inicial", bd("0"), 1),
                new CropStageInput(null, bd("1.0"), 2),
                new CropStageInput("Final", bd("2.5"), 0));

        assertThatThrownBy(() -> service.create(crop("", stages)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "name", "stages[0].kc", "stages[1].name", "stages[2].kc", "stages[2].stageOrder"));
    }

    @Test
    void createRejectsDuplicatedStageOrder() {
        List<CropStageInput> stages = List.of(
                new CropStageInput("A", bd("0.5"), 1), new CropStageInput("B", bd("0.9"), 1));

        assertThatThrownBy(() -> service.create(crop("Milho", stages)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("stages"));
    }

    @Test
    void createRejectsMoreThanTwentyStages() {
        List<CropStageInput> stages = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            stages.add(new CropStageInput("E" + i, bd("1.0"), i));
        }
        assertThatThrownBy(() -> service.create(crop("Milho", stages)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("stages"));
    }

    @Test
    void updateReplacesTheStages() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));

        CropOutput output = service.update(existing.getId(),
                crop("Soja", List.of(new CropStageInput("Único", bd("1.0"), 1))));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.name()).isEqualTo("Soja");
        assertThat(output.stages()).hasSize(1);
    }

    @Test
    void updateFailsWithNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, TestData.cropInput())).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsInvalidData() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), crop(" ", TestData.cropInput().stages())))
                .isInstanceOf(ValidationFailedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesTheCrop() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        verify(repository).deleteById(existing.getId());
    }

    @Test
    void deleteFailsWithNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void getByIdReturnsOrFails() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.getById(existing.getId()).name()).isEqualTo("Milho");
        assertThatThrownBy(() -> service.getById(unknown)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listUsesDefaultsAndRejectsInvalidPagination() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(List.of(TestData.crop()));
        when(repository.count()).thenReturn(1L);

        assertThat(service.list(null, null).size()).isEqualTo(20);
        verify(repository).findAll(PageRequest.of(0, 20));
        assertThatThrownBy(() -> service.list(0, 0)).isInstanceOf(ValidationFailedException.class);
    }
}
