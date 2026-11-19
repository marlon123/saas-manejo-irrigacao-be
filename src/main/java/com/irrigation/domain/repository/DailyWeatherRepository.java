package com.irrigation.domain.repository;

import com.irrigation.domain.model.DailyWeather;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para {@link DailyWeather} (ETP e precipitação diárias).
 * Há no máximo um registro por área e data.
 */
public interface DailyWeatherRepository {

    /**
     * Insere ou substitui, de forma atômica, o registro da área na data.
     * Devolve o registro persistido.
     */
    DailyWeather upsert(DailyWeather weather);

    Optional<DailyWeather> findByAreaAndDate(UUID areaId, LocalDate date);

    /** Registros da área no período (inclusivo), em ordem crescente de data. */
    List<DailyWeather> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to);
}
