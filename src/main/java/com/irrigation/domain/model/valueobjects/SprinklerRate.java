package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando a taxa de aplicação do aspersor em mm/h.
 * (Req 4.4 — maior que zero e no máximo 500 mm/h, até 4 casas decimais)
 */
public record SprinklerRate(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("500");
    private static final int MAX_SCALE = 4;

    public SprinklerRate {
        Objects.requireNonNull(value, "taxa do aspersor não pode ser nula");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("taxa do aspersor deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("taxa do aspersor deve ser no máximo 500 mm/h");
        }
        if (DecimalPlaces.exceeds(value, MAX_SCALE)) {
            throw new IllegalArgumentException("taxa do aspersor deve ter no máximo 4 casas decimais");
        }
    }
}
