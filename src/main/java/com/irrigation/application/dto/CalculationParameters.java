package com.irrigation.application.dto;

import com.irrigation.domain.model.valueobjects.CalculationInput;

import java.math.BigDecimal;

/**
 * Parâmetros de entrada de um cálculo (Req 4.8), tanto para solicitar um cálculo
 * quanto para exibir os dados usados em uma recomendação já armazenada.
 * Valores ausentes são rejeitados pelo motor de cálculo (Req 4.11).
 */
public record CalculationParameters(
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

    public static CalculationParameters from(CalculationInput input) {
        return new CalculationParameters(
                input.getEto(),
                input.getKc() != null ? input.getKc().value() : null,
                input.getEtc(),
                input.getEffectivePrecipitation(),
                input.getCurrentSoilMoisture(),
                input.getWaterStorageCapacity(),
                input.getIrrigationEfficiency() != null ? input.getIrrigationEfficiency().value() : null,
                input.getNetIrrigationDepth(),
                input.getGrossIrrigationDepth(),
                input.getIrrigatedArea(),
                input.getSystemFlowRate(),
                input.getIrrigationTime());
    }
}
