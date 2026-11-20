package com.irrigation.application.dto;

import java.time.LocalDate;

/**
 * Entrada do reprocessamento do balanço hídrico de uma área (Req 13.1).
 *
 * <p>O campo é nulo quando ausente na requisição; a validação (data obrigatória) fica no serviço.
 *
 * @param date data inicial do reprocessamento, que deve ter revisão corrente
 */
public record ReprocessWaterBalanceInput(LocalDate date) {
}
