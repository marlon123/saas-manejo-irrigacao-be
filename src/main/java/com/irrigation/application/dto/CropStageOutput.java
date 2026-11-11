package com.irrigation.application.dto;

import com.irrigation.domain.model.CropStage;

import java.math.BigDecimal;

/**
 * Representação de saída de um estágio de cultura.
 */
public record CropStageOutput(String name, BigDecimal kc, int stageOrder) {

    public static CropStageOutput from(CropStage stage) {
        return new CropStageOutput(stage.getName(), stage.getKc().value(), stage.getStageOrder());
    }
}
