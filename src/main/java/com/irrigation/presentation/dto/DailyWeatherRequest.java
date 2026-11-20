package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo de requisição para registrar (upsert) a meteorologia diária de uma área (Req 5, A-05: a data vai no
 * corpo). Sem anotações de obrigatoriedade ou faixa: o serviço acumula as violações em {@code violations[]}.
 */
@Schema(description = "Meteorologia diária da área (ETP e precipitação)")
public record DailyWeatherRequest(
        @Schema(description = "Data da observação (obrigatória), formato AAAA-MM-DD", example = "2026-04-10")
        LocalDate date,
        @Schema(description = "Evapotranspiração potencial ETP em mm, de 0 a 1000, até 2 casas decimais",
                example = "5.20")
        BigDecimal etpMm,
        @Schema(description = "Precipitação P em mm, de 0 a 1000, até 2 casas decimais", example = "0.00")
        BigDecimal precipitationMm) {
}