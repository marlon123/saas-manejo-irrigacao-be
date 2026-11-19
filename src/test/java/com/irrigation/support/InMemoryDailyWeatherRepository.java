package com.irrigation.support;

import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.repository.DailyWeatherRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Repositório de meteorologia diária em memória. O upsert por área e data mantém uma única linha
 * com o id estável da primeira gravação, como a constraint única do banco.
 */
public class InMemoryDailyWeatherRepository implements DailyWeatherRepository {

    private record Key(UUID areaId, LocalDate date) {
    }

    private final Map<Key, DailyWeather> store = new ConcurrentHashMap<>();

    @Override
    public DailyWeather upsert(DailyWeather weather) {
        Key key = new Key(weather.getAreaId(), weather.getDate());
        return store.compute(key, (k, existing) -> new DailyWeather(
                existing != null ? existing.getId() : weather.getId(),
                weather.getAreaId(), weather.getDate(), weather.getEtp(), weather.getPrecipitation()));
    }

    @Override
    public Optional<DailyWeather> findByAreaAndDate(UUID areaId, LocalDate date) {
        return Optional.ofNullable(store.get(new Key(areaId, date)));
    }

    @Override
    public List<DailyWeather> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return store.values().stream()
                .filter(w -> w.getAreaId().equals(areaId))
                .filter(w -> !w.getDate().isBefore(from) && !w.getDate().isAfter(to))
                .sorted(Comparator.comparing(DailyWeather::getDate))
                .toList();
    }

    /** Todos os registros em ordem de área e data, para conferência nos testes. */
    public List<DailyWeather> findAll() {
        List<DailyWeather> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(DailyWeather::getAreaId).thenComparing(DailyWeather::getDate));
        return all;
    }

    public int count() {
        return store.size();
    }
}
