package com.irrigation.application.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;

import java.math.BigDecimal;

/**
 * Dados de entrada para criar ou atualizar um perfil de solo (Req 3.1, 3.6).
 * Cada parâmetro exige sua unidade de medida explícita (Req 3.14).
 */
public record SoilProfileInput(
        String soilType,
        BigDecimal fieldCapacity,
        BigDecimal permanentWiltingPoint,
        BigDecimal bulkDensity,
        BigDecimal effectiveRootDepth,
        BigDecimal availableWater,
        MeasurementUnit soilWaterUnit,
        MeasurementUnit bulkDensityUnit,
        MeasurementUnit rootDepthUnit,
        MeasurementUnit availableWaterUnit) {
}
