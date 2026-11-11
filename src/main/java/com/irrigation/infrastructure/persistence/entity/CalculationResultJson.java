package com.irrigation.infrastructure.persistence.entity;

import java.math.BigDecimal;

/**
 * Snapshot JSON do {@link com.irrigation.domain.model.valueobjects.CalculationResult}
 * gravado na coluna JSONB {@code recommendations.results} (Req 5.1, 5.7).
 */
public record CalculationResultJson(
        BigDecimal irrigationDepth,
        BigDecimal waterVolume,
        BigDecimal irrigationTime) {
}
