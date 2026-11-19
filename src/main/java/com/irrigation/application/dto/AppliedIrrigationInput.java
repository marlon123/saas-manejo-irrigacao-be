package com.irrigation.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de entrada da irrigação real (IR) aplicada numa área e data (Req 6.1, 6.3 do módulo de balanço
 * hídrico).
 *
 * <p>Tipos brutos e anuláveis, para o serviço acumular as violações (A-05). {@code appliedIrrigationMm}
 * está em mm, de 0 a 1.000, com até 2 casas decimais; zero é válido. A área vem da rota.
 */
public record AppliedIrrigationInput(
        LocalDate date,
        BigDecimal appliedIrrigationMm) {
}
