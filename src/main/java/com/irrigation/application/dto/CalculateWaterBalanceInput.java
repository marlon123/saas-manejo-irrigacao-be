package com.irrigation.application.dto;

import java.time.LocalDate;

/**
 * Entrada do cálculo do balanço hídrico diário de uma área (Req 7.1).
 *
 * <p>Os campos são nulos quando ausentes na requisição; a validação (data obrigatória, TR inteiro maior
 * ou igual a 1) fica no serviço, que acumula todas as violações.
 *
 * @param date     data do balanço (dia civil)
 * @param cycleDay dia do ciclo (TR), inteiro maior ou igual a 1
 */
public record CalculateWaterBalanceInput(LocalDate date, Integer cycleDay) {
}
