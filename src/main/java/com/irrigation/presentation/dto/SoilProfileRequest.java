package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.MeasurementUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Corpo de requisição para criar ou atualizar um perfil de solo (Req 3.1, 3.6).
 * Cada parâmetro vem acompanhado de sua unidade de medida explícita (Req 3.14).
 */
public record SoilProfileRequest(
        @NotBlank @Size(max = 255)
        String soilType,

        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal fieldCapacity,

        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal permanentWiltingPoint,

        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("3.00")
        BigDecimal bulkDensity,

        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000.00")
        BigDecimal effectiveRootDepth,

        @NotNull @DecimalMin("0")
        BigDecimal availableWater,

        @NotNull MeasurementUnit soilWaterUnit,
        @NotNull MeasurementUnit bulkDensityUnit,
        @NotNull MeasurementUnit rootDepthUnit,
        @NotNull MeasurementUnit availableWaterUnit) {
}
