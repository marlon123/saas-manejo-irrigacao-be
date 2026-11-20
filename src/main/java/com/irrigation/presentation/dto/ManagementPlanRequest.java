package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corpo de requisição para criar ou atualizar um plano de manejo (Req 4 do módulo de balanço hídrico).
 *
 * <p>Sem anotações de obrigatoriedade ou faixa: o serviço valida e devolve todas as violações juntas em
 * {@code violations[]}. Na atualização, só {@code fFactor}, {@code sprinklerRateMmPerHour} e {@code endDate}
 * mudam.
 */
@Schema(description = "Dados para criar ou atualizar um plano de manejo da área")
public record ManagementPlanRequest(
        @Schema(description = "Identificador da cultura (obrigatório)")
        UUID cropId,
        @Schema(description = "Identificador do perfil de solo (obrigatório)")
        UUID soilProfileId,
        @Schema(description = "Data de início do plano (obrigatória), formato AAAA-MM-DD", example = "2026-03-01")
        LocalDate startDate,
        @Schema(description = "Data de término do plano (opcional; nula enquanto o plano está ativo)",
                example = "2026-08-31")
        LocalDate endDate,
        @Schema(description = "Fator de disponibilidade F, adimensional, maior que 0 e até 1 (nunca em porcentagem)",
                example = "0.50")
        BigDecimal fFactor,
        @Schema(description = "Taxa do aspersor em mm/h, maior que 0 (obrigatória)", example = "5.00")
        BigDecimal sprinklerRateMmPerHour) {
}