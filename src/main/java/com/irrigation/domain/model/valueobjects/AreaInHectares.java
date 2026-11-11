package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando área em hectares.
 * (Req 1.14 — maior que zero e no máximo 1.000.000,00)
 */
public record AreaInHectares(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("1000000.00");

    public AreaInHectares {
        Objects.requireNonNull(value, "área em hectares não pode ser nula");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("área em hectares deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("área em hectares deve ser no máximo 1.000.000,00");
        }
    }
}
