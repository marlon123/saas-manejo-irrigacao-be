package com.irrigation.presentation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representação de uma recomendação armazenada retornada pela API.
 * Datas e horas estão em UTC.
 */
public record RecommendationResponse(
        UUID id,
        UUID areaId,
        LocalDateTime dateTime,
        CalculationParametersResponse inputs,
        CalculationResultResponse results,
        String methodologyId,
        String methodologyVersion) {
}
