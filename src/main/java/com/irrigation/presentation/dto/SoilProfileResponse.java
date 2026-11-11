package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de um perfil de solo retornado pela API, com unidades explícitas.
 */
public record SoilProfileResponse(
        UUID id,
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
