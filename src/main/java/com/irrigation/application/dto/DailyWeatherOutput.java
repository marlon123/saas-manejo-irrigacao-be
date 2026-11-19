package com.irrigation.application.dto;

import com.irrigation.domain.model.DailyWeather;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Representação de saída da meteorologia diária de uma área, com ETP e precipitação em mm. */
public record DailyWeatherOutput(
        UUID id,
        UUID areaId,
        LocalDate date,
        BigDecimal etpMm,
        BigDecimal precipitationMm) {

    public static DailyWeatherOutput from(DailyWeather weather) {
        return new DailyWeatherOutput(
                weather.getId(),
                weather.getAreaId(),
                weather.getDate(),
                weather.getEtp().value(),
                weather.getPrecipitation().value());
    }
}
