package com.irrigation.application.dto;

import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de saída de um perfil de solo, com unidades explícitas.
 * Os campos opcionais são nulos quando não informados, inclusive em registros anteriores ao módulo
 * de balanço hídrico (Req 1.11).
 */
public record SoilProfileOutput(
        UUID id,
        String soilType,
        BigDecimal fieldCapacity,
        BigDecimal permanentWiltingPoint,
        BigDecimal fieldCapacityGravimetric,
        BigDecimal permanentWiltingPointGravimetric,
        BigDecimal bulkDensity,
        BigDecimal effectiveRootDepth,
        BigDecimal availableWater,
        MeasurementUnit soilWaterUnit,
        MeasurementUnit bulkDensityUnit,
        MeasurementUnit rootDepthUnit,
        MeasurementUnit availableWaterUnit) {

    /**
     * Construtor de compatibilidade (assinatura anterior ao módulo de balanço hídrico):
     * sem o par gravimétrico, que fica nulo.
     */
    public SoilProfileOutput(
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
        this(id, soilType, fieldCapacity, permanentWiltingPoint, null, null, bulkDensity, effectiveRootDepth,
                availableWater, soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }

    public static SoilProfileOutput from(SoilProfile soil) {
        return new SoilProfileOutput(
                soil.getId(),
                soil.getSoilType(),
                soil.getFieldCapacity() == null ? null : soil.getFieldCapacity().value(),
                soil.getPermanentWiltingPoint() == null ? null : soil.getPermanentWiltingPoint().value(),
                soil.getFieldCapacityGravimetric() == null ? null : soil.getFieldCapacityGravimetric().value(),
                soil.getPermanentWiltingPointGravimetric() == null
                        ? null : soil.getPermanentWiltingPointGravimetric().value(),
                soil.getBulkDensity().value(),
                soil.getEffectiveRootDepth() == null ? null : soil.getEffectiveRootDepth().value(),
                soil.getAvailableWater() == null ? null : soil.getAvailableWater().value(),
                soil.getSoilWaterUnit(),
                soil.getBulkDensityUnit(),
                soil.getRootDepthUnit(),
                soil.getAvailableWaterUnit());
    }
}