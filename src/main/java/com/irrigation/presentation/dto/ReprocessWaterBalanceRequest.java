package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Corpo de requisição do reprocessamento do balanço hídrico (Req 13). A validação fica no serviço. */
@Schema(description = "Dados para reprocessar o balanço hídrico a partir de uma data")
public record ReprocessWaterBalanceRequest(
        @Schema(description = "Data inicial do reprocessamento (obrigatória); deve ter revisão corrente",
                example = "2026-04-10")
        LocalDate date) {
}