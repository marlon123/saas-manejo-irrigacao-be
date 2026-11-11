package com.irrigation.domain.calculation;

import java.util.Objects;

/**
 * Identificador único de uma metodologia de cálculo de irrigação.
 * (Req 4.3 — cada metodologia identificada por um identificador único)
 */
public record MethodologyIdentifier(String value) {

    public MethodologyIdentifier {
        Objects.requireNonNull(value, "identificador de metodologia não pode ser nulo");
        if (value.isBlank()) {
            throw new IllegalArgumentException("identificador de metodologia não pode ser vazio");
        }
    }
}
