package com.irrigation.presentation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Representação de uma cultura retornada pela API.
 */
public record CropResponse(
        UUID id,
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageResponse> stages) {
}
