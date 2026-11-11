package com.irrigation.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo de requisição para calcular e armazenar uma recomendação (Req 5.1).
 */
public record CalculateRecommendationRequest(
        @NotNull
        UUID areaId,

        @NotBlank
        String methodologyId,

        @NotNull @Valid
        CalculationParametersRequest inputs) {
}
