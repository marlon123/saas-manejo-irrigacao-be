package com.irrigation.application.validation;

import com.irrigation.domain.exception.DomainException;

import java.util.List;

/**
 * Lançada quando uma ou mais validações de entrada falham. Carrega todas as
 * violações encontradas, para que a camada de apresentação as converta em
 * HTTP 400 com a lista {@code violations} (Req 7.1).
 */
public class ValidationFailedException extends DomainException {

    private final List<FieldViolation> violations;

    public ValidationFailedException(List<FieldViolation> violations) {
        super("Dados inválidos: " + violations.size() + " violação(ões) de validação");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
