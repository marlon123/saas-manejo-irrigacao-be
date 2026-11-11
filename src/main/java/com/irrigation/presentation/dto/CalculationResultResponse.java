package com.irrigation.presentation.dto;

import java.math.BigDecimal;

/**
 * Resultado de um cálculo: lâmina (mm), volume (m³) e tempo (h).
 */
public record CalculationResultResponse(
        BigDecimal irrigationDepth,
        BigDecimal waterVolume,
        BigDecimal irrigationTime) {
}
