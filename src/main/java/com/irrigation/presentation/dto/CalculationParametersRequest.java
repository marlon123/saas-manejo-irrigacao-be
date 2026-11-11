package com.irrigation.presentation.dto;

import java.math.BigDecimal;

/**
 * Parâmetros de entrada de um cálculo (Req 4.8).
 *
 * <p>Sem anotações de intervalo de propósito: ausências e valores fora da faixa são
 * rejeitados pelo motor de cálculo, que responde 400 identificando cada parâmetro (Req 4.11).
 */
public record CalculationParametersRequest(
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
