package com.irrigation.application.service;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
 * Cadastro de solo com campos opcionais e umidade gravimétrica (Req 1.2 a 1.11 do módulo de balanço hídrico).
 * Complementa o {@link SoilProfileServiceTest}, sem repetir a revisão A-01 (unidades opcionais).
 */
@ExtendWith(MockitoExtension.class)
class SoilProfileOptionalFieldsServiceTest {

    @Mock
    SoilProfileRepository repository;

    @InjectMocks
    SoilProfileService service;

    /** Solo mínimo do Req 1.4: tipo, CC e PMP gravimétricos e D. */
    private static SoilProfileInput minimal(String fcGrav, String pmpGrav, String density) {
        return new SoilProfileInput("Argiloso", null, null, dec(fcGrav), dec(pmpGrav), dec(density),
                null, null, null, null, null, null);
    }

    private static SoilProfileInput withVolumetric(String fc, String pmp) {
        return new SoilProfileInput("Argiloso", dec(fc), dec(pmp), null, null, bd("1.30"),
                null, null, null, null, null, null);
    }

    private static BigDecimal dec(String value) {
        return value == null ? null : bd(value);
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    // ---------------------------------------------------- criação e atualização mínimas

    @Test
    void createAcceptsOnlyTypeGravimetricPairAndDensity() {
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.create(minimal("28.50", "14.20", "1.30"));

        assertThat(output.id()).isNotNull();
        assertThat(output.soilType()).isEqualTo("Argiloso");
        assertThat(output.fieldCapacityGravimetric()).isEqualByComparingTo("28.50");
        assertThat(output.permanentWiltingPointGravimetric()).isEqualByComparingTo("14.20");
        assertThat(output.bulkDensity()).isEqualByComparingTo("1.30");
        assertThat(output.fieldCapacity()).isNull();
        assertThat(output.permanentWiltingPoint()).isNull();
        assertThat(output.effectiveRootDepth()).isNull();
        assertThat(output.availableWater()).isNull();
        verify(repository).save(any(SoilProfile.class));
    }

    @Test
    void updateAcceptsOnlyTypeGravimetricPairAndDensityKeepingTheId() {
        SoilProfile existing = TestData.soilProfile();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.update(existing.getId(), minimal("30", "12.5", "1.45"));

        assertThat(output.id()).isEqualTo(existing.getId());
        assertThat(output.fieldCapacityGravimetric()).isEqualByComparingTo("30");
        assertThat(output.permanentWiltingPointGravimetric()).isEqualByComparingTo("12.5");
        assertThat(output.bulkDensity()).isEqualByComparingTo("1.45");
        // PUT substitui o recurso: o par volumétrico do cadastro anterior não é mantido.
        assertThat(output.fieldCapacity()).isNull();
        assertThat(output.permanentWiltingPoint()).isNull();
    }

    @Test
    void createAcceptsBothPairsAtTheSameTime() {
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));
        SoilProfileInput in = new SoilProfileInput("Argiloso", bd("0.35"), bd("0.18"), bd("28.5"), bd("14.2"),
                bd("1.30"), null, null, null, null, null, null);

        SoilProfileOutput output = service.create(in);

        assertThat(output.fieldCapacity()).isEqualByComparingTo("0.35");
        assertThat(output.fieldCapacityGravimetric()).isEqualByComparingTo("28.5");
    }

    @ParameterizedTest
    @CsvSource({"0.01, 0", "100, 99.99", "50.5, 50.49"})
    void createAcceptsGravimetricValuesOnTheBoundaries(String fc, String pmp) {
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.create(minimal(fc, pmp, "3.00"));

        assertThat(output.fieldCapacityGravimetric()).isEqualByComparingTo(fc);
        assertThat(output.permanentWiltingPointGravimetric()).isEqualByComparingTo(pmp);
    }

    // ---------------------------------------------------- par volumétrico incompleto (Req 1.8)

    @Test
    void createRejectsAVolumetricPairWithOnlyFieldCapacity() {
        assertThatThrownBy(() -> service.create(withVolumetric("0.35", null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> {
                    assertThat(fields(e)).containsExactlyInAnyOrder("fieldCapacity", "permanentWiltingPoint");
                    assertThat(e.getViolations()).allSatisfy(v -> assertThat(v.message()).contains("par incompleto"));
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsAVolumetricPairWithOnlyWiltingPoint() {
        assertThatThrownBy(() -> service.create(withVolumetric(null, "0.18")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("fieldCapacity", "permanentWiltingPoint"));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAnIncompleteVolumetricPairWithoutChangingTheStoredProfile() {
        SoilProfile existing = TestData.soilProfile();
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), withVolumetric("0.35", null)))
                .isInstanceOf(ValidationFailedException.class);

        verify(repository, never()).save(any());
        assertThat(existing.getFieldCapacity().value()).isEqualByComparingTo("0.35");
    }

    // ---------------------------------------------------- par gravimétrico incompleto (A-07)

    @ParameterizedTest
    @CsvSource(value = {"28.5, null", "null, 14.2"}, nullValues = "null")
    void createRejectsAGravimetricPairWithOnlyOneValue(String fc, String pmp) {
        assertThatThrownBy(() -> service.create(minimal(fc, pmp, "1.30")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> {
                    assertThat(fields(e)).containsExactlyInAnyOrder(
                            "fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct");
                    assertThat(e.getViolations()).allSatisfy(v -> assertThat(v.message()).contains("par incompleto"));
                });
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------- CC <= PMP (Req 1.2, 1.9)

    @ParameterizedTest
    @CsvSource({"14.20, 14.20", "10, 14.2", "0, 0"})
    void createRejectsGravimetricFieldCapacityNotGreaterThanWiltingPoint(String fc, String pmp) {
        assertThatThrownBy(() -> service.create(minimal(fc, pmp, "1.30")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("fieldCapacityGravimetricPct"));
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------- faixa e escala (Req 1.2, 1.3, 1.9, A-08)

    @ParameterizedTest
    @CsvSource(value = {
            "28.501, 14.20, fieldCapacityGravimetricPct",
            "28.50, 14.123, permanentWiltingPointGravimetricPct",
            "100.01, 14.20, fieldCapacityGravimetricPct",
            "28.50, -0.01, permanentWiltingPointGravimetricPct"})
    void createRejectsGravimetricValuesOutOfRangeOrWithMoreThanTwoDecimals(String fc, String pmp, String field) {
        assertThatThrownBy(() -> service.create(minimal(fc, pmp, "1.30")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly(field));
        verify(repository, never()).save(any());
    }

    @Test
    void createReportsBothGravimetricFieldsWithMoreThanTwoDecimals() {
        assertThatThrownBy(() -> service.create(minimal("28.501", "14.123", "1.30")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct"));
    }

    @Test
    void createAcceptsGravimetricValuesWithTrailingZerosBeyondTwoDecimals() {
        when(repository.save(any(SoilProfile.class))).thenAnswer(i -> i.getArgument(0));

        SoilProfileOutput output = service.create(minimal("28.5000", "14.2000", "1.30"));

        assertThat(output.fieldCapacityGravimetric()).isEqualByComparingTo("28.5");
    }

    @ParameterizedTest
    @CsvSource({"0", "3.01", "-1"})
    void createRejectsABulkDensityOutOfRange(String density) {
        assertThatThrownBy(() -> service.create(minimal("28.5", "14.2", density)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("bulkDensity"));
    }

    @Test
    void createRejectsAMissingBulkDensity() {
        assertThatThrownBy(() -> service.create(minimal("28.5", "14.2", null)))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("bulkDensity"));
    }

    @Test
    void createReportsEveryInvalidGravimetricFieldAtOnce() {
        SoilProfileInput in = new SoilProfileInput(" ", null, null, bd("101"), bd("-1"), bd("0"),
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "soilType", "fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct",
                                "bulkDensity"));
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------- leitura de registro antigo (Req 1.11)

    @Test
    void getByIdOfALegacySoilReturnsNullGravimetricValuesAndKeepsTheOthers() {
        // TestData.soilProfile() usa o construtor anterior ao módulo, como um registro antigo (sem par gravimétrico).
        SoilProfile legacy = TestData.soilProfile();
        when(repository.findById(legacy.getId())).thenReturn(Optional.of(legacy));

        SoilProfileOutput output = service.getById(legacy.getId());

        assertThat(output.fieldCapacityGravimetric()).isNull();
        assertThat(output.permanentWiltingPointGravimetric()).isNull();
        assertThat(output.fieldCapacity()).isEqualByComparingTo("0.35");
        assertThat(output.permanentWiltingPoint()).isEqualByComparingTo("0.18");
        assertThat(output.bulkDensity()).isEqualByComparingTo("1.30");
        assertThat(output.effectiveRootDepth()).isEqualByComparingTo("600.00");
        assertThat(output.availableWater()).isEqualByComparingTo("100.00");
        assertThat(output.soilWaterUnit()).isEqualTo(legacy.getSoilWaterUnit());
        assertThat(output.bulkDensityUnit()).isEqualTo(legacy.getBulkDensityUnit());
    }

    @Test
    void outputOfASoilWithOnlyTheMandatoryFieldsHasNullOptionalFields() {
        SoilProfile soil = new SoilProfile(UUID.randomUUID(), "Arenoso", null, null,
                new BulkDensity(bd("1.5")), null, null, null, null, null, null);

        SoilProfileOutput output = SoilProfileOutput.from(soil);

        assertThat(output.soilType()).isEqualTo("Arenoso");
        assertThat(output.bulkDensity()).isEqualByComparingTo("1.5");
        assertThat(output.fieldCapacity()).isNull();
        assertThat(output.permanentWiltingPoint()).isNull();
        assertThat(output.fieldCapacityGravimetric()).isNull();
        assertThat(output.permanentWiltingPointGravimetric()).isNull();
        assertThat(output.effectiveRootDepth()).isNull();
        assertThat(output.availableWater()).isNull();
        assertThat(output.soilWaterUnit()).isNull();
        assertThat(output.bulkDensityUnit()).isNull();
        assertThat(output.rootDepthUnit()).isNull();
        assertThat(output.availableWaterUnit()).isNull();
    }
}
