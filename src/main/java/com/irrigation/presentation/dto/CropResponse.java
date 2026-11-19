package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Representação de uma cultura retornada pela API.
 * {@code kc} e {@code rootDepthMm} são nulos em culturas anteriores ao módulo de balanço hídrico (Req 2.7).
 */
public record CropResponse(
        UUID id,
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageResponse> stages,
        @Schema(description = "Coeficiente de cultura Kc (adimensional); nulo em registros antigos", nullable = true)
        BigDecimal kc,
        @Schema(description = "Profundidade radicular Z em milímetros; nulo em registros antigos", nullable = true)
        BigDecimal rootDepthMm) {

    /** Construtor de compatibilidade (assinatura anterior ao módulo): {@code kc} e {@code rootDepthMm} nulos. */
    public CropResponse(UUID id, String name, String variety, Integer cultivationCycleDays,
                        List<CropStageResponse> stages) {
        this(id, name, variety, cultivationCycleDays, stages, null, null);
    }
}