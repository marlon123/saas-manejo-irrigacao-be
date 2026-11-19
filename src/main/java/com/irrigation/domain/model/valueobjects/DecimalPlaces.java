package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * Utilitário interno dos value objects do balanço hídrico para contar casas decimais.
 * Zeros à direita não contam como casa decimal (2,50 tem 1 casa; 10 tem 0).
 */
final class DecimalPlaces {

    private DecimalPlaces() {
    }

    static boolean exceeds(BigDecimal value, int maxPlaces) {
        return value.signum() != 0 && value.stripTrailingZeros().scale() > maxPlaces;
    }
}
