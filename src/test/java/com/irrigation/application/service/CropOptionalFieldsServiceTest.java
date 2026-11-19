package com.irrigation.application.service;

import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cadastro de cultura com KC e Z e estágios opcionais (Req 2.2 a 2.11 do módulo de balanço hídrico).
 * Complementa o {@link CropServiceTest}, sem repetir a revisão A-01 (lista vazia aceita e 21 estágios recusados).
 */
@ExtendWith(MockitoExtension.class)
class CropOptionalFieldsServiceTest {

    @Mock
    CropRepository repository;

    @InjectMocks
    CropService service;

    private static CropInput onlyNameKcAndDepth(String kc, String depth) {
        return new CropInput("Milho", null, null, null, dec(kc), dec(depth));
    }

    private static BigDecimal dec(String value) {
        return value == null ? null : bd(value);
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    private static List<CropStageInput> stages(int count) {
        List<CropStageInput> stages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            stages.add(new CropStageInput("E" + i, bd("1.0"), i));
        }
        return stages;
    }

    // ---------------------------------------------------- criação e atualização mínimas (Req 2.4)

    @Test
    void createAcceptsOnlyNameKcAndRootDepthWithZeroStages() {
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));

        CropOutput output = service.create(onlyNameKcAndDepth("1.15", "450.50"));

        assertThat(output.id()).isNotNull();
        assertThat(output.name()).isEqualTo("Milho");
        assertThat(output.kc()).isEqualByComparingTo("1.15");
        assertThat(output.rootDepth()).isEqualByComparingTo("450.50");
        assertThat(output.stages()).isEmpty();
        assertThat(output.variety()).isNull();
        assertThat(output.cultivationCycleDays()).isNull();
    }

    @Test
    void updateAcceptsOnlyNameKcAndRootDepthAndDropsTheStoredStages() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));

        CropOutput output = service.update(existing.getId(), onlyNameKcAndDepth("0.9", "300"));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.kc()).isEqualByComparingTo("0.9");
        assertThat(output.rootDepth()).isEqualByComparingTo("300");
        assertThat(output.stages()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0.01, 0.01", "2.0, 5000", "2.00, 5000.00"})
    void createAcceptsKcAndRootDepthOnTheBoundaries(String kc, String depth) {
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));

        CropOutput output = service.create(onlyNameKcAndDepth(kc, depth));

        assertThat(output.kc()).isEqualByComparingTo(kc);
        assertThat(output.rootDepth()).isEqualByComparingTo(depth);
    }

    // ---------------------------------------------------- KC e Z ausentes ou fora da faixa (Req 2.10)

    @Test
    void createReportsMissingKcAndRootDepthTogether() {
        assertThatThrownBy(() -> service.create(onlyNameKcAndDepth(null, null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("kc", "rootDepthMm"));
        verify(repository, never()).save(any());
    }

    @Test
    void createReportsOnlyAMissingKc() {
        assertThatThrownBy(() -> service.create(onlyNameKcAndDepth(null, "400")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("kc"));
    }

    @Test
    void createReportsOnlyAMissingRootDepth() {
        assertThatThrownBy(() -> service.create(onlyNameKcAndDepth("1.0", null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("rootDepthMm"));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 400, kc",
            "-0.5, 400, kc",
            "2.01, 400, kc",
            "1.005, 400, kc",
            "1.0, 0, rootDepthMm",
            "1.0, 5000.01, rootDepthMm",
            "1.0, 400.123, rootDepthMm"})
    // Colunas: KC, Z (mm) e o campo que deve aparecer como violação.
    void createRejectsKcOrRootDepthOutOfRangeOrWithMoreThanTwoDecimals(String kc, String depth, String field) {
        assertThatThrownBy(() -> service.create(onlyNameKcAndDepth(kc, depth)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly(field));
        verify(repository, never()).save(any());
    }

    @Test
    void createReportsKcRootDepthAndNameAtOnce() {
        CropInput in = new CropInput(" ", null, null, List.of(), bd("3"), bd("-1"));

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("name", "kc", "rootDepthMm"));
    }

    @Test
    void updateWithAMissingKcKeepsTheStoredCropUnchanged() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), onlyNameKcAndDepth(null, "300")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("kc"));

        verify(repository, never()).save(any());
        assertThat(existing.getName()).isEqualTo("Milho");
        assertThat(existing.getStages()).hasSize(2);
    }

    // ---------------------------------------------------- limite de 20 estágios (Req 2.5, 2.11)

    @Test
    void createAcceptsExactlyTwentyStages() {
        when(repository.save(any(Crop.class))).thenAnswer(i -> i.getArgument(0));
        CropInput in = new CropInput("Milho", null, null, stages(20), bd("1.0"), bd("400"));

        assertThat(service.create(in).stages()).hasSize(20);
    }

    @Test
    void createRejectsTwentyOneStagesEvenWhenAnotherStageIsInvalid() {
        List<CropStageInput> list = stages(21);
        list.set(0, new CropStageInput("", bd("1.0"), 1));
        CropInput in = new CropInput("Milho", null, null, list, bd("1.0"), bd("400"));

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("stages[0].name", "stages"));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsTwentyOneStagesWithoutChangingTheStoredCrop() {
        Crop existing = TestData.crop();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        CropInput in = new CropInput("Milho", null, null, stages(21), bd("1.0"), bd("400"));

        assertThatThrownBy(() -> service.update(existing.getId(), in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("stages"));

        verify(repository, never()).save(any());
        assertThat(existing.getStages()).hasSize(2);
    }

    // ---------------------------------------------------- leitura de registro antigo (Req 2.7)

    @Test
    void getByIdOfALegacyCropReturnsNullKcAndRootDepthAndKeepsTheOtherValues() {
        // TestData.crop() usa o construtor anterior ao módulo, como um registro antigo (sem KC e Z).
        Crop legacy = TestData.crop();
        when(repository.findById(legacy.getId())).thenReturn(Optional.of(legacy));

        CropOutput output = service.getById(legacy.getId());

        assertThat(output.kc()).isNull();
        assertThat(output.rootDepth()).isNull();
        assertThat(output.name()).isEqualTo("Milho");
        assertThat(output.variety()).isEqualTo("Híbrido");
        assertThat(output.cultivationCycleDays()).isEqualTo(120);
        assertThat(output.stages()).extracting("stageOrder").containsExactly(1, 2);
        assertThat(output.stages().get(0).kc()).isEqualByComparingTo("0.40");
        assertThat(output.stages().get(1).kc()).isEqualByComparingTo("1.15");
    }
}
