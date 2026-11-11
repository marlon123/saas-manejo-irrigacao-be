package com.irrigation.presentation.dto;

import java.math.BigDecimal;

/**
 * Representação de um estágio de cultura retornado pela API.
 */
public record CropStageResponse(String name, BigDecimal kc, int stageOrder) {
}
