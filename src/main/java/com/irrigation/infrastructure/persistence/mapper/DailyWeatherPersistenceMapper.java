package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.infrastructure.persistence.entity.DailyWeatherEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link DailyWeatherEntity} (persistência) e {@link DailyWeather} (domínio).
 */
@Component
public class DailyWeatherPersistenceMapper {

    public DailyWeather toDomain(DailyWeatherEntity entity) {
        return new DailyWeather(
                entity.getId(),
                entity.getAreaId(),
                entity.getWeatherDate(),
                new EtpDepth(entity.getEtpMm()),
                new PrecipitationDepth(entity.getPrecipitationMm()));
    }

    public DailyWeatherEntity toEntity(DailyWeather weather) {
        DailyWeatherEntity entity = DailyWeatherEntity.newInstance();
        entity.setId(weather.getId());
        entity.setAreaId(weather.getAreaId());
        entity.setWeatherDate(weather.getDate());
        entity.setEtpMm(weather.getEtp().value());
        entity.setPrecipitationMm(weather.getPrecipitation().value());
        return entity;
    }
}
