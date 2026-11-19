package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.DailyWeatherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyWeatherJpaRepository extends JpaRepository<DailyWeatherEntity, UUID> {

    Optional<DailyWeatherEntity> findByAreaIdAndWeatherDate(UUID areaId, LocalDate weatherDate);

    /** Registros da área no período (inclusivo), em ordem crescente de data. */
    List<DailyWeatherEntity> findByAreaIdAndWeatherDateBetweenOrderByWeatherDateAsc(
            UUID areaId, LocalDate from, LocalDate to);

    /**
     * Insere ou substitui, de forma atômica, a linha da área na data. Em conflito mantém o
     * {@code id} existente e atualiza os valores. {@code updated_at} fica a cargo do trigger.
     * O contexto de persistência é limpo para que a leitura seguinte veja a linha gravada.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO daily_weather (id, area_id, weather_date, etp_mm, precipitation_mm)
            VALUES (:id, :areaId, :weatherDate, :etpMm, :precipitationMm)
            ON CONFLICT (area_id, weather_date) DO UPDATE
               SET etp_mm = EXCLUDED.etp_mm,
                   precipitation_mm = EXCLUDED.precipitation_mm
            """, nativeQuery = true)
    int upsert(@Param("id") UUID id,
               @Param("areaId") UUID areaId,
               @Param("weatherDate") LocalDate weatherDate,
               @Param("etpMm") BigDecimal etpMm,
               @Param("precipitationMm") BigDecimal precipitationMm);
}
