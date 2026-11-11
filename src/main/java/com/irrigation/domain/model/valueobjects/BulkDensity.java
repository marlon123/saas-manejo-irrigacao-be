package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando densidade aparente do solo em g/cm³.
 * (Req 3.12 — maior que zero e no máximo 3,00)
 */
public record BulkDensity(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("3.00");

    public BulkDensity {
        Objects.requireNonNull(value, "densidade aparente não pode ser nula");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("densidade aparente deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("densidade aparente deve ser no máximo 3,00 g/cm³");
        }
    }
}
