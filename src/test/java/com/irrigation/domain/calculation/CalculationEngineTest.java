package com.irrigation.domain.calculation;

import com.irrigation.domain.calculation.exception.DuplicateMethodologyException;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.support.FakeCalculationMethod;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.irrigation.support.TestData.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculationEngineTest {

    private CalculationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CalculationEngine();
    }

    @Test
    void delegatesToTheRegisteredMethod() {
        FakeCalculationMethod method = new FakeCalculationMethod("m1");
        engine.register(method);

        CalculationResult result = engine.calculate(new MethodologyIdentifier("m1"), TestData.calculationInput());

        assertThat(result).isEqualTo(TestData.calculationResult());
        assertThat(method.calls()).isEqualTo(1);
    }

    @Test
    void failsForAnUnregisteredMethodology() {
        assertThatThrownBy(() -> engine.calculate(new MethodologyIdentifier("nada"), TestData.calculationInput()))
                .isInstanceOf(MethodologyNotAvailableException.class);
    }

    @Test
    void rejectsDuplicateRegistrationAndKeepsTheOriginal() {
        FakeCalculationMethod original = new FakeCalculationMethod("m1", "1.0", TestData.calculationResult());
        FakeCalculationMethod duplicate = new FakeCalculationMethod("m1", "2.0",
                new CalculationResult(bd("1"), bd("1"), bd("1")));
        engine.register(original);

        assertThatThrownBy(() -> engine.register(duplicate)).isInstanceOf(DuplicateMethodologyException.class);

        engine.calculate(new MethodologyIdentifier("m1"), TestData.calculationInput());
        assertThat(original.calls()).isEqualTo(1);
        assertThat(duplicate.calls()).isZero();
    }

    @Test
    void doesNotExecuteWhenTheInputIsInvalid() {
        FakeCalculationMethod method = new FakeCalculationMethod("m1");
        engine.register(method);
        CalculationInput invalid = new CalculationInput(bd("99"), null, bd("5"), bd("0"), bd("50"), bd("100"),
                null, bd("20"), bd("25"), bd("10"), bd("100"), bd("2"));

        assertThatThrownBy(() -> engine.calculate(new MethodologyIdentifier("m1"), invalid))
                .isInstanceOfSatisfying(InvalidCalculationInputException.class, e ->
                        assertThat(e.getInvalidParameters())
                                .containsExactlyInAnyOrder("ETo", "Kc", "eficiência do sistema de irrigação"));
        assertThat(method.calls()).isZero();
    }

    @Test
    void validatesInputBeforeCheckingTheMethodology() {
        CalculationInput invalid = new CalculationInput(null, null, null, null, null, null, null, null, null,
                null, null, null);
        assertThatThrownBy(() -> engine.calculate(new MethodologyIdentifier("nada"), invalid))
                .isInstanceOf(InvalidCalculationInputException.class);
    }

    @Test
    void severalMethodsCanCoexist() {
        engine.register(new FakeCalculationMethod("a"));
        engine.register(new FakeCalculationMethod("b"));

        assertThat(engine.isRegistered(new MethodologyIdentifier("a"))).isTrue();
        assertThat(engine.isRegistered(new MethodologyIdentifier("b"))).isTrue();
        assertThat(engine.getRegisteredMethods()).hasSize(2);
    }

    @Test
    void registeredMethodsViewIsReadOnly() {
        assertThatThrownBy(() -> engine.getRegisteredMethods().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void calculationResultRejectsValuesOutsideTheRanges() {
        assertThatThrownBy(() -> new CalculationResult(new BigDecimal("500.01"), BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculationResult(BigDecimal.ONE, new BigDecimal("1000000.00"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculationResult(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculationResult(new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void methodologyIdentifierMustNotBeBlank() {
        assertThatThrownBy(() -> new MethodologyIdentifier(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
