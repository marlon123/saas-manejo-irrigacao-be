package com.irrigation.application.dto;

import com.irrigation.domain.model.Crop;

import java.util.List;
import java.util.UUID;

/**
 * Representação de saída de uma cultura, com seus estágios ordenados.
 */
public record CropOutput(
        UUID id,
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageOutput> stages) {

    public static CropOutput from(Crop crop) {
        return new CropOutput(
                crop.getId(),
                crop.getName(),
                crop.getVariety(),
                crop.getCultivationCycleDays(),
                crop.getStages().stream().map(CropStageOutput::from).toList());
    }
}
