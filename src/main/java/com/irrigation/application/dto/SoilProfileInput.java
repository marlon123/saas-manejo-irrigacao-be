package com.irrigation.application.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;

import java.math.BigDecimal;

/**
 * Dados de entrada para criar ou atualizar um perfil de solo (Req 3.1, 3.6 da spec original; Req 1 do módulo
 * de balanço hídrico).
 *
 * <p>Obrigatórios: tipo de solo e densidade aparente. Os demais campos são opcionais: cada par de umidade
 * (volumétrico e gravimétrico) é "ambos ou nenhum", e as unidades, a profundidade efetiva e a água disponível
 * aceitam nulo (Req 1.4, 1.6, 1.8).
 */
public record SoilProfileInput(
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
    public SoilProfileInput(
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
        this(soilType, fieldCapacity, permanentWiltingPoint, null, null, bulkDensity, effectiveRootDepth,
                availableWater, soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }
}
