package com.irrigation.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada da meteorologia diária de uma área (Req 5.1, 5.2 do módulo de balanço hídrico).
 *
 * <p>Tipos brutos e anuláveis, para o serviço acumular as violações (A-05). {@code etpMm} (ETP) e
 * {@code precipitationMm} (P) estão em mm, de 0 a 1.000, com até 2 casas decimais. A área vem da rota.
 */
public record DailyWeatherInput(
        LocalDate date,
        BigDecimal etpMm,
        BigDecimal precipitationMm) {
}
