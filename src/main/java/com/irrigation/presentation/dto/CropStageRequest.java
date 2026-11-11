package com.irrigation.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Estágio de desenvolvimento dentro do corpo de uma cultura (Req 2.12, 2.13).
 */
public record CropStageRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("2.0")
        BigDecimal kc,

        @NotNull @Positive
        Integer stageOrder) {
}
