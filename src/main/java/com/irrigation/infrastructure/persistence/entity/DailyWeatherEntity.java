package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code daily_weather}. Separado do agregado de domínio
 * {@link com.irrigation.domain.model.DailyWeather}. As colunas {@code created_at} e
 * {@code updated_at} são mantidas só pelo banco e não são mapeadas.
 */
@Entity
@Table(name = "daily_weather")
public class DailyWeatherEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "weather_date", nullable = false)
    private LocalDate weatherDate;

    @Column(name = "etp_mm", nullable = false, precision = 8, scale = 3)
    private BigDecimal etpMm;

    @Column(name = "precipitation_mm", nullable = false, precision = 8, scale = 3)
    private BigDecimal precipitationMm;

    protected DailyWeatherEntity() {
        // exigido pelo JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public LocalDate getWeatherDate() { return weatherDate; }
    public void setWeatherDate(LocalDate weatherDate) { this.weatherDate = weatherDate; }
    public BigDecimal getEtpMm() { return etpMm; }
    public void setEtpMm(BigDecimal etpMm) { this.etpMm = etpMm; }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public void setPrecipitationMm(BigDecimal precipitationMm) { this.precipitationMm = precipitationMm; }

    public static DailyWeatherEntity newInstance() {
        return new DailyWeatherEntity();
    }
}
