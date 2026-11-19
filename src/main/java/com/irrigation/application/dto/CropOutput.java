package com.irrigation.application.dto;

import com.irrigation.domain.model.Crop;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Representação de saída de uma cultura, com seus estágios ordenados.
 * {@code kc} e {@code rootDepth} (Z, em mm) são nulos em culturas anteriores ao módulo de balanço hídrico
 * (Req 2.7).
 */
public record CropOutput(
        UUID id,
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageOutput> stages,
        BigDecimal kc,
        BigDecimal rootDepth) {

    /** Construtor de compatibilidade (assinatura anterior ao módulo): {@code kc} e {@code rootDepth} nulos. */
    public CropOutput(UUID id, String name, String variety, Integer cultivationCycleDays,
                      List<CropStageOutput> stages) {
        this(id, name, variety, cultivationCycleDays, stages, null, null);
    }

    public static CropOutput from(Crop crop) {
        return new CropOutput(
                crop.getId(),
                crop.getName(),
                crop.getVariety(),
                crop.getCultivationCycleDays(),
                crop.getStages().stream().map(CropStageOutput::from).toList(),
                crop.getKc() == null ? null : crop.getKc().value(),
                crop.getRootDepth() == null ? null : crop.getRootDepth().value());
    }
}
