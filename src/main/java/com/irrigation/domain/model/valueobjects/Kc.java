package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando o coeficiente de cultura (Kc).
 * (Req 2.13 — maior que zero e no máximo 2,0)
 */
public record Kc(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("2.0");

    public Kc {
        Objects.requireNonNull(value, "Kc não pode ser nulo");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("Kc deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Kc deve ser no máximo 2,0");
        }
    }
}
