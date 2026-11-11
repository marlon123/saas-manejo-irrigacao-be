package com.irrigation.application.dto;

import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de saída de um perfil de solo, com unidades explícitas.
 */
public record SoilProfileOutput(
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

    public static SoilProfileOutput from(SoilProfile soil) {
        return new SoilProfileOutput(
                soil.getId(),
                soil.getSoilType(),
                soil.getFieldCapacity().value(),
                soil.getPermanentWiltingPoint().value(),
                soil.getBulkDensity().value(),
                soil.getEffectiveRootDepth().value(),
                soil.getAvailableWater().value(),
                soil.getSoilWaterUnit(),
                soil.getBulkDensityUnit(),
                soil.getRootDepthUnit(),
                soil.getAvailableWaterUnit());
    }
}
