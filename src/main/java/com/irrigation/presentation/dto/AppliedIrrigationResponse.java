package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Representação da irrigação real (IR) aplicada numa área e data. */
@Schema(description = "Irrigação real (IR) aplicada na área")
public record AppliedIrrigationResponse(
        @Schema(description = "Identificador do registro") UUID id,
        @Schema(description = "Identificador da área") UUID areaId,
        @Schema(description = "Data da irrigação", example = "2026-04-10") LocalDate date,
        @Schema(description = "Lâmina aplicada em mm", example = "12.50") BigDecimal appliedIrrigationMm) {
}