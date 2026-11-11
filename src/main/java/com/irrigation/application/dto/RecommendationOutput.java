package com.irrigation.application.dto;

import com.irrigation.domain.model.Recommendation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representação de saída de uma recomendação armazenada (Req 5.1).
 */
public record RecommendationOutput(
        UUID id,
        UUID areaId,
        LocalDateTime dateTime,
        CalculationParameters inputs,
        CalculationResultOutput results,
        String methodologyId,
        String methodologyVersion) {

    public static RecommendationOutput from(Recommendation r) {
        return new RecommendationOutput(
                r.getId(),
                r.getAreaId(),
                r.getDateTime(),
                CalculationParameters.from(r.getInputData()),
                CalculationResultOutput.from(r.getResults()),
                r.getMethodologyId().value(),
                r.getMethodologyVersion());
    }
}
