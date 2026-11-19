package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Corpo de requisição para criar ou atualizar um perfil de solo (Req 3.1, 3.6 da spec original; Req 1 do
 * módulo de balanço hídrico).
 *
 * <p>Obrigatórios: tipo de solo e densidade aparente. Cada par de umidade (volumétrico e gravimétrico) é
 * "ambos ou nenhum"; as unidades, a profundidade efetiva e a água disponível são opcionais (Req 1.4, 1.6, 1.8).
 */
public record SoilProfileRequest(
        @NotBlank @Size(max = 255)
        String soilType,
        @DecimalMin("0") @DecimalMax("1")
        BigDecimal fieldCapacity,
        @DecimalMin("0") @DecimalMax("1")
        BigDecimal permanentWiltingPoint,
        @Schema(description = "Capacidade de campo gravimétrica em % (0 a 100, até 2 casas decimais); "
                + "deve ser informada junto com permanentWiltingPointGravimetricPct", example = "28.50")
        @DecimalMin("0") @DecimalMax("100")
        BigDecimal fieldCapacityGravimetricPct,
        @Schema(description = "Ponto de murcha permanente gravimétrico em % (0 a 100, até 2 casas decimais), "
                + "menor que a capacidade de campo; deve ser informado junto com fieldCapacityGravimetricPct",
                example = "14.20")
        @DecimalMin("0") @DecimalMax("100")
        BigDecimal permanentWiltingPointGravimetricPct,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("3.00")
        BigDecimal bulkDensity,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000.00")
        BigDecimal effectiveRootDepth,
        @DecimalMin("0")
        BigDecimal availableWater,
        MeasurementUnit soilWaterUnit,
        MeasurementUnit bulkDensityUnit,
        MeasurementUnit rootDepthUnit,
        MeasurementUnit availableWaterUnit) {

    /** Construtor de compatibilidade (assinatura anterior ao módulo): par gravimétrico nulo. */
    public SoilProfileRequest(
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