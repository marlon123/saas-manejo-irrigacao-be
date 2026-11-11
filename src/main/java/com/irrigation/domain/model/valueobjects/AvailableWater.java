package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando água disponível no solo em milímetros.
 * (Req 3.10 — Item em Aberto 3 — unidade mm)
 */
public record AvailableWater(BigDecimal value) {

    public AvailableWater {
        Objects.requireNonNull(value, "água disponível não pode ser nula");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("água disponível não pode ser negativa");
        }
    }
}
