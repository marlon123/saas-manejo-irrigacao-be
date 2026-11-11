package com.irrigation.presentation.dto;

import java.math.BigDecimal;

/**
 * Parâmetros de entrada usados em uma recomendação armazenada.
 */
public record CalculationParametersResponse(
        BigDecimal eto,
        BigDecimal kc,
        BigDecimal etc,
        BigDecimal effectivePrecipitation,
        BigDecimal currentSoilMoisture,
        BigDecimal waterStorageCapacity,
        BigDecimal irrigationEfficiency,
        BigDecimal netIrrigationDepth,
        BigDecimal grossIrrigationDepth,
        BigDecimal irrigatedArea,
        BigDecimal systemFlowRate,
        BigDecimal irrigationTime) {
}
