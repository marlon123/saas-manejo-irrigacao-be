package com.irrigation.support;

import com.irrigation.domain.calculation.CalculationMethod;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metodologia falsa para testes: devolve um resultado fixo e conta as invocações.
 * Não implementa nenhuma fórmula agronômica.
 */
public class FakeCalculationMethod implements CalculationMethod {

    private final MethodologyIdentifier id;
    private final String version;
    private final CalculationResult result;
    private final AtomicInteger calls = new AtomicInteger();

    public FakeCalculationMethod(String id, String version, CalculationResult result) {
        this.id = new MethodologyIdentifier(id);
        this.version = version;
        this.result = result;
    }

    public FakeCalculationMethod(String id) {
        this(id, "1.0", TestData.calculationResult());
    }

    @Override
    public MethodologyIdentifier methodologyId() {
        return id;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public CalculationResult calculate(CalculationInput input) {
        calls.incrementAndGet();
        return result;
    }

    public int calls() {
        return calls.get();
    }
}
