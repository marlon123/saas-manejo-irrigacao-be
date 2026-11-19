package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.repository.DailyWeatherRepository;
import com.irrigation.infrastructure.persistence.mapper.DailyWeatherPersistenceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link DailyWeatherRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class DailyWeatherRepositoryAdapter implements DailyWeatherRepository {

    private final DailyWeatherJpaRepository jpa;
    private final DailyWeatherPersistenceMapper mapper;

    public DailyWeatherRepositoryAdapter(DailyWeatherJpaRepository jpa, DailyWeatherPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DailyWeather upsert(DailyWeather weather) {
        // INSERT ... ON CONFLICT atômico; a leitura seguinte, na mesma transação, devolve a linha persistida
        // (com o id existente, quando houve conflito).
        jpa.upsert(weather.getId(), weather.getAreaId(), weather.getDate(),
                weather.getEtp().value(), weather.getPrecipitation().value());
        return jpa.findByAreaIdAndWeatherDate(weather.getAreaId(), weather.getDate())
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "Registro meteorológico não encontrado após o upsert: área "
                                + weather.getAreaId() + ", data " + weather.getDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DailyWeather> findByAreaAndDate(UUID areaId, LocalDate date) {
        return jpa.findByAreaIdAndWeatherDate(areaId, date).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyWeather> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return jpa.findByAreaIdAndWeatherDateBetweenOrderByWeatherDateAsc(areaId, from, to)
                .stream().map(mapper::toDomain).toList();
    }
}