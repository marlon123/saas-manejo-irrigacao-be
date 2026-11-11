package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando profundidade efetiva de raízes em milímetros.
 * (Req 3.13 — maior que zero e no máximo 5.000,00 mm)
 */
public record RootDepth(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("5000.00");

    public RootDepth {
        Objects.requireNonNull(value, "profundidade efetiva de raízes não pode ser nula");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("profundidade efetiva de raízes deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("profundidade efetiva de raízes deve ser no máximo 5.000,00 mm");
        }
    }
}
