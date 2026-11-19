package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando a Irrigação Real (IR) aplicada no dia, em mm.
 * (Req 6.3 — de 0 a 1.000 mm inclusive, até 2 casas decimais; zero é válido)
 */
public record AppliedIrrigationDepth(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("1000");
    private static final int MAX_SCALE = 2;

    public AppliedIrrigationDepth {
        Objects.requireNonNull(value, "irrigação real não pode ser nula");
        if (value.compareTo(MIN) < 0) {
            throw new IllegalArgumentException("irrigação real não pode ser negativa");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("irrigação real deve ser no máximo 1.000 mm");
        }
        if (DecimalPlaces.exceeds(value, MAX_SCALE)) {
            throw new IllegalArgumentException("irrigação real deve ter no máximo 2 casas decimais");
        }
    }
}
