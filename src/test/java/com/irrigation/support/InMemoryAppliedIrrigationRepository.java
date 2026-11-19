package com.irrigation.support;

import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.repository.AppliedIrrigationRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório de irrigação aplicada em memória. O upsert por área e data mantém uma única linha
 * com o id estável da primeira gravação, como a constraint única do banco.
 */
public class InMemoryAppliedIrrigationRepository implements AppliedIrrigationRepository {

    private record Key(UUID areaId, LocalDate date) {
    }

    private final Map<Key, AppliedIrrigation> store = new ConcurrentHashMap<>();

    @Override
    public AppliedIrrigation upsert(AppliedIrrigation irrigation) {
        Key key = new Key(irrigation.getAreaId(), irrigation.getDate());
        return store.compute(key, (k, existing) -> new AppliedIrrigation(
                existing != null ? existing.getId() : irrigation.getId(),
                irrigation.getAreaId(), irrigation.getDate(), irrigation.getDepth()));
    }

    @Override
    public Optional<AppliedIrrigation> findByAreaAndDate(UUID areaId, LocalDate date) {
        return Optional.ofNullable(store.get(new Key(areaId, date)));
    }

    @Override
    public List<AppliedIrrigation> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return store.values().stream()
                .filter(i -> i.getAreaId().equals(areaId))
                .filter(i -> !i.getDate().isBefore(from) && !i.getDate().isAfter(to))
                .sorted(Comparator.comparing(AppliedIrrigation::getDate))
                .toList();
    }

    /** Todos os registros em ordem de área e data, para conferência nos testes. */
    public List<AppliedIrrigation> findAll() {
        List<AppliedIrrigation> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(AppliedIrrigation::getAreaId).thenComparing(AppliedIrrigation::getDate));
        return all;
    }

    public int count() {
        return store.size();
    }
}
