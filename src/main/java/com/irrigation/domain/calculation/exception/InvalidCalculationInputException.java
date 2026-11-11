package com.irrigation.domain.calculation.exception;

import com.irrigation.domain.exception.DomainException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lançada quando os parâmetros de entrada de cálculo estão ausentes ou fora dos
 * intervalos válidos. Identifica exatamente quais parâmetros são inválidos.
 * (Req 4.11) Mapeia para HTTP 400.
 */
public class InvalidCalculationInputException extends DomainException {

    private final List<String> invalidParameters;

    public InvalidCalculationInputException(List<String> invalidParameters) {
        super(String.format("Parâmetros de cálculo inválidos: %s", String.join(", ", invalidParameters)));
        this.invalidParameters = Collections.unmodifiableList(Objects.requireNonNull(invalidParameters));
    }

    public List<String> getInvalidParameters() {
        return invalidParameters;
    }
}
