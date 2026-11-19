package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando a evapotranspiração potencial diária (ETP) em mm.
 * (Req 5.2 — de 0 a 1.000 mm inclusive, até 2 casas decimais)
 */
public record EtpDepth(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("1000");
    private static final int MAX_SCALE = 2;

    public EtpDepth {
        Objects.requireNonNull(value, "ETP não pode ser nula");
        if (value.compareTo(MIN) < 0) {
            throw new IllegalArgumentException("ETP não pode ser negativa");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("ETP deve ser no máximo 1.000 mm");
        }
        if (DecimalPlaces.exceeds(value, MAX_SCALE)) {
            throw new IllegalArgumentException("ETP deve ter no máximo 2 casas decimais");
        }
    }
}
