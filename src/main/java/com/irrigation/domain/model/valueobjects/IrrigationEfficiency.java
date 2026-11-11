package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando eficiência do sistema de irrigação como fração.
 * (Req 4.8 — intervalo 0,01 a 1 como fração; Item em Aberto 11)
 */
public record IrrigationEfficiency(BigDecimal value) {

    private static final BigDecimal MIN = new BigDecimal("0.01");
    private static final BigDecimal MAX = BigDecimal.ONE;

    public IrrigationEfficiency {
        Objects.requireNonNull(value, "eficiência de irrigação não pode ser nula");
        if (value.compareTo(MIN) < 0) {
            throw new IllegalArgumentException("eficiência de irrigação deve ser no mínimo 0,01");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("eficiência de irrigação deve ser no máximo 1,0");
        }
    }
}
