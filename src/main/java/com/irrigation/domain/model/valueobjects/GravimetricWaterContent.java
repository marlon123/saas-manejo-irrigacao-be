package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando a umidade gravimétrica do solo, em porcentagem (g de água por 100 g de solo).
 * (Req 1.2, A-08 — de 0 a 100 inclusive e no máximo 2 casas decimais, igual a NUMERIC(5,2))
 */
public record GravimetricWaterContent(BigDecimal value) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = new BigDecimal("100");
    private static final int MAX_SCALE = 2;

    public GravimetricWaterContent {
        Objects.requireNonNull(value, "umidade gravimétrica não pode ser nula");
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("umidade gravimétrica deve estar entre 0 e 100");
        }
        // stripTrailingZeros evita recusar 1.500 (escala 3, mas só 1 casa significativa)
        if (value.signum() != 0 && value.stripTrailingZeros().scale() > MAX_SCALE) {
            throw new IllegalArgumentException("umidade gravimétrica deve ter no máximo 2 casas decimais");
        }
    }
}
