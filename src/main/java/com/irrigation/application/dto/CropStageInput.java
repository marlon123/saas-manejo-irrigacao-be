package com.irrigation.application.dto;

import java.math.BigDecimal;

/**
 * Estágio de desenvolvimento informado na criação/atualização de uma cultura.
 */
public record CropStageInput(String name, BigDecimal kc, Integer stageOrder) {
}
