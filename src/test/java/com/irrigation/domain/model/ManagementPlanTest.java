package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes do agregado {@link ManagementPlan} (Req 4.1 a 4.5, A-02). */
class ManagementPlanTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);

    private ManagementPlan activePlan() {
        return TestData.managementPlan(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), START);
    }

    @Test
    void newPlanWithoutEndDateIsActive() {
        ManagementPlan plan = activePlan();

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getEndDate()).isNull();
        assertThat(plan.getAvailabilityFactor().value()).isEqualByComparingTo("0.5");
        assertThat(plan.getSprinklerRate().value()).isEqualByComparingTo("10");
    }

    @Test
    void planWithEndDateIsNotActive() {
        ManagementPlan plan = TestData.managementPlan(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                START, START.plusDays(5));

        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void constructorRejectsMissingRequiredReferencesAndParameters() {
        AvailabilityFactor f = new AvailabilityFactor(bd("0.5"));
        SprinklerRate r = new SprinklerRate(bd("10"));
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new ManagementPlan(null, id, id, id, START, f, r)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, null, id, id, START, f, r)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, id, null, id, START, f, r)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, id, id, null, START, f, r)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, id, id, id, null, f, r)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, id, id, id, START, null, r)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagementPlan(id, id, id, id, START, f, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsEndDateBeforeStartDateAndAcceptsEqual() {
        UUID id = UUID.randomUUID();
        AvailabilityFactor f = new AvailabilityFactor(bd("0.5"));
        SprinklerRate r = new SprinklerRate(bd("10"));

        assertThatThrownBy(() -> new ManagementPlan(id, id, id, id, START, START.minusDays(1), f, r))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ManagementPlan(id, id, id, id, START, START, f, r).getEndDate()).isEqualTo(START);
    }

    @Test
    void closeSetsTheEndDate() {
        ManagementPlan plan = activePlan();

        plan.close(START.plusDays(30));

        assertThat(plan.getEndDate()).isEqualTo(START.plusDays(30));
        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void closeOnTheStartDateIsAccepted() {
        ManagementPlan plan = activePlan();

        plan.close(START);

        assertThat(plan.getEndDate()).isEqualTo(START);
    }

    @Test
    void closeRejectsNullAndDateBeforeStartAndKeepsThePlanActive() {
        ManagementPlan plan = activePlan();

        assertThatThrownBy(() -> plan.close(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.close(START.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(plan.isActive()).isTrue();
    }

    @Test
    void updateParametersReplacesBothValues() {
        ManagementPlan plan = activePlan();

        plan.updateParameters(new AvailabilityFactor(bd("0.7")), new SprinklerRate(bd("12.5")));

        assertThat(plan.getAvailabilityFactor().value()).isEqualByComparingTo("0.7");
        assertThat(plan.getSprinklerRate().value()).isEqualByComparingTo("12.5");
    }

    @Test
    void updateParametersIsAtomicWhenOneValueIsMissing() {
        ManagementPlan plan = activePlan();

        assertThatThrownBy(() -> plan.updateParameters(new AvailabilityFactor(bd("0.9")), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.updateParameters(null, new SprinklerRate(bd("99"))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(plan.getAvailabilityFactor().value()).isEqualByComparingTo("0.5");
        assertThat(plan.getSprinklerRate().value()).isEqualByComparingTo("10");
    }

    @Test
    void validateMethodsAcceptValidAndRejectInvalidValues() {
        assertThatCode(() -> ManagementPlan.validateStartDate(START)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ManagementPlan.validateStartDate(null)).isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> ManagementPlan.validateEndDate(START, null)).doesNotThrowAnyException();
        assertThatCode(() -> ManagementPlan.validateEndDate(null, START)).doesNotThrowAnyException();
        assertThatCode(() -> ManagementPlan.validateEndDate(START, START)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ManagementPlan.validateEndDate(START, START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> ManagementPlan.validateAvailabilityFactor(new AvailabilityFactor(bd("1"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ManagementPlan.validateAvailabilityFactor(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> ManagementPlan.validateSprinklerRate(new SprinklerRate(bd("500"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ManagementPlan.validateSprinklerRate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueObjectsRejectFactorAndRateOutOfRange() {
        assertThatThrownBy(() -> new AvailabilityFactor(bd("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilityFactor(bd("1.0001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SprinklerRate(bd("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SprinklerRate(bd("500.01"))).isInstanceOf(IllegalArgumentException.class);
    }
}
