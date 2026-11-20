package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo de requisição para registrar (upsert) a irrigação real (IR) aplicada (Req 6, A-05: a data vai no
 * corpo). Sem anotações de obrigatoriedade ou faixa: o serviço acumula as violações em {@code violations[]}.
 */
@Schema(description = "Irrigação real (IR) aplicada na área")
public record AppliedIrrigationRequest(
        @Schema(description = "Data da irrigação (obrigatória), formato AAAA-MM-DD", example = "2026-04-10")
        LocalDate date,
        @Schema(description = "Lâmina aplicada IR em mm, de 0 a 1000, até 2 casas decimais; zero é válido",
                example = "12.50")
        BigDecimal appliedIrrigationMm) {
}