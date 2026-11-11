package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando conteúdo de água volumétrico.
 * (Req 3.11 — intervalo 0,00 a 1,00; Item em Aberto 3)
 */
public record VolumetricWaterContent(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = BigDecimal.ONE;

    public VolumetricWaterContent {
        Objects.requireNonNull(value, "conteúdo de água volumétrico não pode ser nulo");
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("conteúdo de água volumétrico deve estar entre 0,00 e 1,00");
        }
    }
}
