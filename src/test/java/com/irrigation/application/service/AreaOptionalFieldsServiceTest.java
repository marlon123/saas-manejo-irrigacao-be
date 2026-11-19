package com.irrigation.application.service;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * Cadastro de área com campos opcionais (Req 3.1 a 3.4 do módulo de balanço hídrico).
 * Complementa o {@link AreaServiceTest}, sem repetir a revisão A-01 (só o nome é exigido).
 */
@ExtendWith(MockitoExtension.class)
class AreaOptionalFieldsServiceTest {

    @Mock
    AreaRepository repository;

    @InjectMocks
    AreaService service;

    private static AreaInput onlyName(String name) {
        return new AreaInput(name, null, null, null, null, null, null, null);
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    @Test
    void createWithOnlyANameStoresEveryOtherFieldAsAbsent() {
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));

        AreaOutput output = service.create(onlyName("Talhão 2"));

        assertThat(output.id()).isNotNull();
        assertThat(output.name()).isEqualTo("Talhão 2");
        assertThat(output.areaHectares()).isNull();
        assertThat(output.irrigationSystemType()).isNull();
        assertThat(output.systemFlowRate()).isNull();
        assertThat(output.latitude()).isNull();
        assertThat(output.longitude()).isNull();
        assertThat(output.irrigationEfficiency()).isNull();
    }

    @Test
    void updateWithOnlyANameClearsTheOptionalFieldsAndKeepsTheId() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));

        AreaOutput output = service.update(existing.getId(), onlyName("Só o nome"));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.name()).isEqualTo("Só o nome");
        assertThat(output.areaHectares()).isNull();
        assertThat(output.irrigationSystemType()).isNull();
        assertThat(output.systemFlowRate()).isNull();
    }

    @Test
    void createRejectsABlankNameEvenWhenEverythingElseIsAbsent() {
        assertThatThrownBy(() -> service.create(onlyName("   ")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("name"));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsANameLongerThan255Characters() {
        assertThatThrownBy(() -> service.create(onlyName("a".repeat(256))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("name"));
    }

    @ParameterizedTest
    @CsvSource({"0.01, 0.01", "1000000.00, 1000000.00"})
    void createAcceptsPresentValuesOnTheBoundaries(String hectares, String flow) {
        when(repository.save(any(Area.class))).thenAnswer(i -> i.getArgument(0));
        AreaInput in = new AreaInput("A", bd(hectares), null, null, null, IrrigationSystemType.CENTER_PIVOT, bd(flow), null);

        AreaOutput output = service.create(in);

        assertThat(output.areaHectares()).isEqualByComparingTo(hectares);
        assertThat(output.systemFlowRate()).isEqualByComparingTo(flow);
        assertThat(output.irrigationSystemType()).isEqualTo(IrrigationSystemType.CENTER_PIVOT);
    }

    @ParameterizedTest
    @CsvSource({
            "1000000.01, 1, areaHectares",
            "0, 1, areaHectares",
            "1, 1000000.01, systemFlowRate",
            "1, 0, systemFlowRate"})
    void createRejectsPresentValuesOutOfRange(String hectares, String flow, String field) {
        AreaInput in = new AreaInput("A", bd(hectares), null, null, null, null, bd(flow), null);

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly(field));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectingAPresentValueOutOfRangeDoesNotSave() {
        Area existing = TestData.area();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        AreaInput in = new AreaInput("A", bd("-1"), null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(existing.getId(), in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("areaHectares"));

        verify(repository, never()).save(any());
        assertThat(existing.getAreaHectares().value()).isEqualByComparingTo("10.50");
    }

    // ---------------------------------------------------- leitura de registro antigo (Req 3.4)

    @Test
    void getByIdOfAnAreaWithEveryFieldFilledKeepsTheStoredValues() {
        Area stored = TestData.area();
        when(repository.findById(stored.getId())).thenReturn(Optional.of(stored));

        AreaOutput output = service.getById(stored.getId());

        assertThat(output.name()).isEqualTo("Talhão 1");
        assertThat(output.areaHectares()).isEqualByComparingTo("10.50");
        assertThat(output.irrigationSystemType()).isEqualTo(IrrigationSystemType.DRIP);
        assertThat(output.systemFlowRate()).isEqualByComparingTo("120.50");
        assertThat(output.irrigationEfficiency()).isEqualByComparingTo("0.85");
        assertThat(output.generalCharacteristics()).isEqualTo("solo argiloso");
    }

    @Test
    void getByIdOfAnAreaWithAbsentFieldsReturnsNulls() {
        Area stored = new Area(UUID.randomUUID(), "Sem dados", null, null, null, null, null, null);
        when(repository.findById(stored.getId())).thenReturn(Optional.of(stored));

        AreaOutput output = service.getById(stored.getId());

        assertThat(output.name()).isEqualTo("Sem dados");
        assertThat(output.areaHectares()).isNull();
        assertThat(output.irrigationSystemType()).isNull();
        assertThat(output.systemFlowRate()).isNull();
    }
}
