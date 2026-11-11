package com.irrigation.application.dto;

import java.util.List;

/**
 * Dados de entrada para criar ou atualizar uma cultura (Req 2.1, 2.7).
 */
public record CropInput(
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageInput> stages) {
}
