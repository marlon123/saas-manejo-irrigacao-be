package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando o fator de disponibilidade (F) do plano de manejo.
 * Valor adimensional, nunca dividido por 100 (Req 4.3 — maior que 0 e no máximo 1, até 4 casas decimais).
 */
public record AvailabilityFactor(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = BigDecimal.ONE;
    private static final int MAX_SCALE = 4;

    public AvailabilityFactor {
        Objects.requireNonNull(value, "fator de disponibilidade (F) não pode ser nulo");
        if (value.compareTo(MIN) <= 0) {
            throw new IllegalArgumentException("fator de disponibilidade (F) deve ser maior que zero");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("fator de disponibilidade (F) deve ser no máximo 1");
        }
        if (DecimalPlaces.exceeds(value, MAX_SCALE)) {
            throw new IllegalArgumentException(
                    "fator de disponibilidade (F) deve ter no máximo 4 casas decimais");
        }
    }
}
