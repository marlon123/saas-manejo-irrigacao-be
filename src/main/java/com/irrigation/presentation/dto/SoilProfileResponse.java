package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de um perfil de solo retornado pela API, com unidades explícitas.
 * Os campos opcionais são nulos quando não informados, inclusive em registros anteriores ao módulo de
 * balanço hídrico (Req 1.11).
 */
public record SoilProfileResponse(
        UUID id,
        String soilType,
        BigDecimal fieldCapacity,
        BigDecimal permanentWiltingPoint,
        @Schema(description = "Capacidade de campo gravimétrica em %; nulo quando não informada", nullable = true)
        BigDecimal fieldCapacityGravimetricPct,
        @Schema(description = "Ponto de murcha permanente gravimétrico em %; nulo quando não informado",
                nullable = true)
        BigDecimal permanentWiltingPointGravimetricPct,
        BigDecimal bulkDensity,
        BigDecimal effectiveRootDepth,
        BigDecimal availableWater,
        MeasurementUnit soilWaterUnit,
        MeasurementUnit bulkDensityUnit,
        MeasurementUnit rootDepthUnit,
        MeasurementUnit availableWaterUnit) {

    /** Construtor de compatibilidade (assinatura anterior ao módulo): par gravimétrico nulo. */
    public SoilProfileResponse(
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
}