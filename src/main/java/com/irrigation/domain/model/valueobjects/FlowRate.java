package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando vazão do sistema de irrigação em m³/h.
 * (Req 1.15 — maior que zero e no máximo 1.000.000,00; Item em Aberto 2 — unidade m³/h)
 */
public record FlowRate(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("1000000.00");

    public FlowRate {
        Objects.requireNonNull(value, "vazão do sistema não pode ser nula");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("vazão do sistema deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("vazão do sistema deve ser no máximo 1.000.000,00");
        }
    }
}
