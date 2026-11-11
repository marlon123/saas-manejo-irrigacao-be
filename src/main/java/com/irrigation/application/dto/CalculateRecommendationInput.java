package com.irrigation.application.dto;

import java.util.UUID;

/**
 * Solicitação de cálculo de uma recomendação de irrigação (Req 5.1).
 */
public record CalculateRecommendationInput(
        UUID areaId,
        String methodologyId,
        CalculationParameters parameters) {
}
