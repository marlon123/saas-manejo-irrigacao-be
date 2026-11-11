package com.irrigation.infrastructure.persistence.entity;

import java.math.BigDecimal;

/**
 * Snapshot JSON do {@link com.irrigation.domain.model.valueobjects.CalculationInput}
 * gravado na coluna JSONB {@code recommendations.input_data} (Req 5.1, 5.7).
 *
 * <p>Os campos podem ser nulos porque o value object de domínio aceita valores
 * ausentes (a validação ocorre no motor de cálculo).
 */
public record CalculationInputJson(
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
