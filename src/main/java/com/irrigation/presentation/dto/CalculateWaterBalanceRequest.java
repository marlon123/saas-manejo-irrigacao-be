package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Corpo de requisição do cálculo do balanço hídrico diário (Req 7). Sem anotações de obrigatoriedade: o
 * serviço valida e acumula as violações em {@code violations[]}.
 */
@Schema(description = "Dados para calcular o balanço hídrico diário da área")
public record CalculateWaterBalanceRequest(
        @Schema(description = "Data do balanço (obrigatória), formato AAAA-MM-DD", example = "2026-04-10")
        LocalDate date,
        @Schema(description = "Dia do ciclo (TR), inteiro maior ou igual a 1 (obrigatório)", example = "42")
        Integer cycleDay) {
}