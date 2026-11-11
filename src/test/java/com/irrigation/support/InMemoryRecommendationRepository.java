package com.irrigation.support;

import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.RecommendationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Repositório em memória com a mesma semântica de filtro do adaptador JPA
 * (intervalo de datas inclusivo, mais recentes primeiro).
 */
public class InMemoryRecommendationRepository implements RecommendationRepository {

    private final List<Recommendation> store = new ArrayList<>();

    @Override
    public Recommendation save(Recommendation recommendation) {
        store.add(recommendation);
        return recommendation;
    }

    @Override
    public Optional<Recommendation> findById(UUID id) {
        return store.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    @Override
    public List<Recommendation> findByArea(UUID areaId, PageRequest pageRequest) {
        return page(r -> r.getAreaId().equals(areaId), pageRequest);
    }

    @Override
    public long countByArea(UUID areaId) {
        return count(r -> r.getAreaId().equals(areaId));
    }

    @Override
    public List<Recommendation> findByDateRange(LocalDateTime from, LocalDateTime to, PageRequest pageRequest) {
        return page(inRange(from, to), pageRequest);
    }

    @Override
    public long countByDateRange(LocalDateTime from, LocalDateTime to) {
        return count(inRange(from, to));
    }

    @Override
    public List<Recommendation> findByAreaAndDateRange(
            UUID areaId, LocalDateTime from, LocalDateTime to, PageRequest pageRequest) {
        return page(r -> r.getAreaId().equals(areaId) && inRange(from, to).test(r), pageRequest);
    }

    @Override
    public long countByAreaAndDateRange(UUID areaId, LocalDateTime from, LocalDateTime to) {
        return count(r -> r.getAreaId().equals(areaId) && inRange(from, to).test(r));
    }

    @Override
    public long count() {
        return store.size();
    }

    public Set<UUID> referencedAreaIds() {
        return store.stream().map(Recommendation::getAreaId).collect(Collectors.toSet());
    }

    private Predicate<Recommendation> inRange(LocalDateTime from, LocalDateTime to) {
        return r -> !r.getDateTime().isBefore(from) && !r.getDateTime().isAfter(to);
    }

    private List<Recommendation> page(Predicate<Recommendation> filter, PageRequest pageRequest) {
        return store.stream()
                .filter(filter)
                .sorted(Comparator.comparing(Recommendation::getDateTime).reversed())
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
    }

    private long count(Predicate<Recommendation> filter) {
        return store.stream().filter(filter).count();
    }
}
