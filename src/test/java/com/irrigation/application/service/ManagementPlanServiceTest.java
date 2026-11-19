package com.irrigation.application.service;

import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.InMemoryAreaRepository;
import com.irrigation.support.InMemoryManagementPlanRepository;
import com.irrigation.support.InMemoryRecommendationRepository;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testes unitários do {@link ManagementPlanService} (Req 4, 11.6), sem Spring nem Docker. */
class ManagementPlanServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private InMemoryManagementPlanRepository plans;
    private InMemoryAreaRepository areas;
    private CropRepository crops;
    private SoilProfileRepository soils;
    private ManagementPlanService service;

    private UUID areaId;
    private UUID cropId;
    private UUID soilId;

    @BeforeEach
    void setUp() {
        plans = new InMemoryManagementPlanRepository();
        areas = new InMemoryAreaRepository(new InMemoryRecommendationRepository());
        crops = mock(CropRepository.class);
        soils = mock(SoilProfileRepository.class);
        service = new ManagementPlanService(plans, areas, crops, soils);

        areaId = areas.save(TestData.area()).getId();
        cropId = UUID.randomUUID();
        soilId = UUID.randomUUID();
        when(crops.existsById(cropId)).thenReturn(true);
        when(soils.existsById(soilId)).thenReturn(true);
    }

    private ManagementPlanInput input(LocalDate start, LocalDate end) {
        return new ManagementPlanInput(cropId, soilId, start, end, bd("0.5"), bd("10"));
    }

    private static List<String> fields(ValidationFailedException e) {
        return e.getViolations().stream().map(FieldViolation::field).toList();
    }

    // ------------------------------------------------------------ criação

    @Test
    void createPersistsAnActivePlan() {
        ManagementPlanOutput out = service.create(areaId, input(START, null));

        assertThat(out.areaId()).isEqualTo(areaId);
        assertThat(out.endDate()).isNull();
        assertThat(out.fFactor()).isEqualByComparingTo("0.5");
        assertThat(out.sprinklerRateMmPerHour()).isEqualByComparingTo("10");
        assertThat(plans.count()).isEqualTo(1);
    }

    @Test
    void createReportsEveryInvalidFieldAtOnce() {
        ManagementPlanInput in = new ManagementPlanInput(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(areaId, in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder(
                                "cropId", "soilProfileId", "startDate", "fFactor", "sprinklerRateMmPerHour"));
        assertThat(plans.count()).isZero();
    }

    @Test
    void createRejectsOutOfRangeFactorAndZeroRate() {
        ManagementPlanInput in = new ManagementPlanInput(cropId, soilId, START, null, bd("1.5"), bd("0"));

        assertThatThrownBy(() -> service.create(areaId, in))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("fFactor", "sprinklerRateMmPerHour"));
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> service.create(areaId, input(START, START.minusDays(1))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("endDate"));
    }

    @Test
    void createAcceptsEndDateEqualToStartDate() {
        ManagementPlanOutput out = service.create(areaId, input(START, START));

        assertThat(out.endDate()).isEqualTo(START);
    }

    @Test
    void createWithUnknownAreaIs404() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), input(START, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Área");
        assertThat(plans.count()).isZero();
    }

    @Test
    void createWithUnknownCropIs404() {
        ManagementPlanInput in = new ManagementPlanInput(UUID.randomUUID(), soilId, START, null, bd("0.5"), bd("10"));

        assertThatThrownBy(() -> service.create(areaId, in))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Cultura");
        assertThat(plans.count()).isZero();
    }

    @Test
    void createWithUnknownSoilIs404() {
        ManagementPlanInput in = new ManagementPlanInput(cropId, UUID.randomUUID(), START, null, bd("0.5"), bd("10"));

        assertThatThrownBy(() -> service.create(areaId, in))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Solo");
        assertThat(plans.count()).isZero();
    }

    @Test
    void validationErrorWinsOverMissingArea() {
        ManagementPlanInput in = new ManagementPlanInput(cropId, soilId, START, null, null, bd("10"));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), in))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void missingResourceWinsOverBusinessRule() {
        service.create(areaId, input(START, null));
        ManagementPlanInput in = new ManagementPlanInput(UUID.randomUUID(), soilId, START.plusDays(10), null,
                bd("0.5"), bd("10"));

        assertThatThrownBy(() -> service.create(areaId, in)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void secondActivePlanIs422() {
        service.create(areaId, input(START, null));

        assertThatThrownBy(() -> service.create(areaId, input(START.plusDays(30), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ativo");
        assertThat(plans.count()).isEqualTo(1);
    }

    @Test
    void overlappingClosedPlanIs422() {
        service.create(areaId, input(START, LocalDate.of(2026, 3, 31)));

        assertThatThrownBy(() -> service.create(areaId, input(LocalDate.of(2026, 3, 31), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sobrepõe");
        assertThat(plans.count()).isEqualTo(1);
    }

    @Test
    void closedPlanOverlappingTheActiveOneIs422() {
        service.create(areaId, input(START, null));

        assertThatThrownBy(() -> service.create(areaId, input(START.minusDays(10), START)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adjacentPeriodsDoNotOverlap() {
        service.create(areaId, input(START, LocalDate.of(2026, 3, 31)));

        service.create(areaId, input(LocalDate.of(2026, 4, 1), null));

        assertThat(plans.count()).isEqualTo(2);
    }

    @Test
    void closedPlanBeforeTheActiveOneIsAccepted() {
        service.create(areaId, input(START, null));

        service.create(areaId, input(START.minusYears(1), START.minusDays(1)));

        assertThat(plans.count()).isEqualTo(2);
    }

    // ---------------------------------------------------------- atualização

    private ManagementPlanOutput createActive() {
        return service.create(areaId, input(START, null));
    }

    @Test
    void updateChangesFactorAndRateWithoutChangingRecordedBalances() {
        ManagementPlanOutput plan = createActive();
        plans.registerBalance(plan.id(), START.plusDays(6));

        ManagementPlanOutput out = service.update(areaId, plan.id(),
                new ManagementPlanInput(null, null, null, null, bd("0.7"), bd("15")));

        assertThat(out.fFactor()).isEqualByComparingTo("0.7");
        assertThat(out.sprinklerRateMmPerHour()).isEqualByComparingTo("15");
        assertThat(out.cropId()).isEqualTo(cropId);
        assertThat(out.soilProfileId()).isEqualTo(soilId);
        assertThat(plans.hasBalances(plan.id())).isTrue();
        assertThat(plans.lastCalculatedDate(plan.id())).contains(START.plusDays(6));
    }

    @Test
    void updateWithInvalidFieldsIs400AndKeepsThePlan() {
        ManagementPlanOutput plan = createActive();

        assertThatThrownBy(() -> service.update(areaId, plan.id(),
                new ManagementPlanInput(null, null, null, null, null, bd("501"))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactlyInAnyOrder("fFactor", "sprinklerRateMmPerHour"));
        assertThat(plans.findById(plan.id()).orElseThrow().getAvailabilityFactor().value()).isEqualByComparingTo("0.5");
    }

    @Test
    void updateWithUnknownAreaIs404() {
        ManagementPlanOutput plan = createActive();

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), plan.id(), input(null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateWithUnknownPlanOrPlanOfAnotherAreaIs404() {
        ManagementPlanOutput plan = createActive();
        UUID otherArea = areas.save(TestData.area()).getId();

        assertThatThrownBy(() -> service.update(areaId, UUID.randomUUID(), input(null, null)))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.update(otherArea, plan.id(), input(null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateWithUnknownNewCropOrSoilIs404() {
        ManagementPlanOutput plan = createActive();

        assertThatThrownBy(() -> service.update(areaId, plan.id(),
                new ManagementPlanInput(UUID.randomUUID(), null, null, null, bd("0.5"), bd("10"))))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Cultura");
        assertThatThrownBy(() -> service.update(areaId, plan.id(),
                new ManagementPlanInput(null, UUID.randomUUID(), null, null, bd("0.5"), bd("10"))))
                .isInstanceOf(EntityNotFoundException.class).hasMessageContaining("Solo");
    }

    @Test
    void changingCropOrSoilWithBalancesIs422() {
        ManagementPlanOutput plan = createActive();
        plans.registerBalance(plan.id(), START);
        UUID newCrop = UUID.randomUUID();
        UUID newSoil = UUID.randomUUID();
        when(crops.existsById(newCrop)).thenReturn(true);
        when(soils.existsById(newSoil)).thenReturn(true);

        assertThatThrownBy(() -> service.update(areaId, plan.id(),
                new ManagementPlanInput(newCrop, null, null, null, bd("0.5"), bd("10"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("balanços");
        assertThatThrownBy(() -> service.update(areaId, plan.id(),
                new ManagementPlanInput(null, newSoil, null, null, bd("0.5"), bd("10"))))
                .isInstanceOf(BusinessRuleException.class);
        ManagementPlan stored = plans.findById(plan.id()).orElseThrow();
        assertThat(stored.getCropId()).isEqualTo(cropId);
        assertThat(stored.getSoilProfileId()).isEqualTo(soilId);
    }

    @Test
    void changingCropAndSoilWithoutBalancesIsAccepted() {
        ManagementPlanOutput plan = createActive();
        UUID newCrop = UUID.randomUUID();
        UUID newSoil = UUID.randomUUID();
        when(crops.existsById(newCrop)).thenReturn(true);
        when(soils.existsById(newSoil)).thenReturn(true);

        ManagementPlanOutput out = service.update(areaId, plan.id(),
                new ManagementPlanInput(newCrop, newSoil, null, null, bd("0.5"), bd("10")));

        assertThat(out.cropId()).isEqualTo(newCrop);
        assertThat(out.soilProfileId()).isEqualTo(newSoil);
    }

    @Test
    void repeatingTheSameCropAndSoilWithBalancesIsAccepted() {
        ManagementPlanOutput plan = createActive();
        plans.registerBalance(plan.id(), START);

        ManagementPlanOutput out = service.update(areaId, plan.id(),
                new ManagementPlanInput(cropId, soilId, START, null, bd("0.6"), bd("10")));

        assertThat(out.fFactor()).isEqualByComparingTo("0.6");
    }

    @Test
    void startDateCannotBeChanged() {
        ManagementPlanOutput plan = createActive();

        assertThatThrownBy(() -> service.update(areaId, plan.id(), input(START.plusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("início");
    }

    @Test
    void endDateBeforeStartDateOnUpdateIs400() {
        ManagementPlanOutput plan = createActive();

        assertThatThrownBy(() -> service.update(areaId, plan.id(), input(null, START.minusDays(1))))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(fields(e)).containsExactly("endDate"));
    }

    @Test
    void endDateBeforeLastCalculatedDayIs422() {
        ManagementPlanOutput plan = createActive();
        LocalDate last = START.plusDays(9);
        plans.registerBalance(plan.id(), last);

        assertThatThrownBy(() -> service.update(areaId, plan.id(), input(null, last.minusDays(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("último dia calculado");
        assertThat(plans.findById(plan.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void endDateEqualToLastCalculatedDayIsAccepted() {
        ManagementPlanOutput plan = createActive();
        LocalDate last = START.plusDays(9);
        plans.registerBalance(plan.id(), last);

        ManagementPlanOutput out = service.update(areaId, plan.id(), input(null, last));

        assertThat(out.endDate()).isEqualTo(last);
    }

    @Test
    void endDateAfterLastCalculatedDayIsAccepted() {
        ManagementPlanOutput plan = createActive();
        plans.registerBalance(plan.id(), START.plusDays(9));

        ManagementPlanOutput out = service.update(areaId, plan.id(), input(null, START.plusDays(20)));

        assertThat(out.endDate()).isEqualTo(START.plusDays(20));
    }

    @Test
    void closingThePlanLetsAnotherActivePlanBeCreated() {
        ManagementPlanOutput plan = createActive();
        service.update(areaId, plan.id(), input(null, LocalDate.of(2026, 3, 31)));

        service.create(areaId, input(LocalDate.of(2026, 4, 1), null));

        assertThat(plans.count()).isEqualTo(2);
    }

    @Test
    void reopeningAClosedPlanWhileAnotherIsActiveIs422() {
        ManagementPlanOutput first = service.create(areaId, input(START, LocalDate.of(2026, 3, 31)));
        service.create(areaId, input(LocalDate.of(2026, 4, 1), null));

        assertThatThrownBy(() -> service.update(areaId, first.id(), input(null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void extendingEndDateIntoAnotherPlanIs422() {
        ManagementPlanOutput first = service.create(areaId, input(START, LocalDate.of(2026, 3, 31)));
        service.create(areaId, input(LocalDate.of(2026, 4, 1), null));

        assertThatThrownBy(() -> service.update(areaId, first.id(), input(null, LocalDate.of(2026, 4, 10))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("sobrepõe");
    }

    // -------------------------------------------------------------- consulta

    @Test
    void listByAreaReturnsPlansByStartDateDescending() {
        ManagementPlanOutput oldest = service.create(areaId, input(START, LocalDate.of(2026, 3, 31)));
        ManagementPlanOutput active = service.create(areaId, input(LocalDate.of(2026, 7, 1), null));
        ManagementPlanOutput middle = service.create(areaId, input(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));

        assertThat(service.listByArea(areaId)).extracting(ManagementPlanOutput::id)
                .containsExactly(active.id(), middle.id(), oldest.id());
    }

    @Test
    void listByAreaWithoutPlansIsEmpty() {
        assertThat(service.listByArea(areaId)).isEmpty();
    }

    @Test
    void listByUnknownAreaIs404() {
        assertThatThrownBy(() -> service.listByArea(UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listByAreaDoesNotReturnPlansOfOtherAreas() {
        createActive();
        UUID otherArea = areas.save(TestData.area()).getId();

        assertThat(service.listByArea(otherArea)).isEmpty();
    }
}
