package com.irrigation.application.dto;

import com.irrigation.domain.model.valueobjects.CalculationResult;

import java.math.BigDecimal;

/**
 * Resultado de um cálculo: lâmina (mm), volume (m³) e tempo (h) — Req 4.7.
 */
public record CalculationResultOutput(
        BigDecimal irrigationDepth,
        BigDecimal waterVolume,
        BigDecimal irrigationTime) {

    public static CalculationResultOutput from(CalculationResult result) {
        return new CalculationResultOutput(
                result.getIrrigationDepth(), result.getWaterVolume(), result.getIrrigationTime());
    }
}
