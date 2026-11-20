package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Representação de um plano de manejo. {@code endDate} é nula enquanto o plano está ativo. */
@Schema(description = "Plano de manejo de uma área")
public record ManagementPlanResponse(
        @Schema(description = "Identificador do plano") UUID id,
        @Schema(description = "Identificador da área") UUID areaId,
        @Schema(description = "Identificador da cultura") UUID cropId,
        @Schema(description = "Identificador do perfil de solo") UUID soilProfileId,
        @Schema(description = "Data de início do plano", example = "2026-03-01") LocalDate startDate,
        @Schema(description = "Data de término do plano; nula enquanto ativo", nullable = true) LocalDate endDate,
        @Schema(description = "Fator de disponibilidade F (adimensional, de 0 exclusive a 1)", example = "0.50")
        BigDecimal fFactor,
        @Schema(description = "Taxa do aspersor em mm/h", example = "5.00") BigDecimal sprinklerRateMmPerHour) {
}