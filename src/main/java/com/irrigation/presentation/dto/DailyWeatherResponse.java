package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Representação da meteorologia diária de uma área. */
@Schema(description = "Meteorologia diária da área")
public record DailyWeatherResponse(
        @Schema(description = "Identificador do registro") UUID id,
        @Schema(description = "Identificador da área") UUID areaId,
        @Schema(description = "Data da observação", example = "2026-04-10") LocalDate date,
        @Schema(description = "ETP em mm", example = "5.20") BigDecimal etpMm,
        @Schema(description = "Precipitação em mm", example = "0.00") BigDecimal precipitationMm) {
}