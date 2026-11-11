package com.irrigation.support;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de áreas em memória. A referência por recomendações é consultada
 * no {@link InMemoryRecommendationRepository} informado.
 */
public class InMemoryAreaRepository implements AreaRepository {

    private final Map<UUID, Area> store = new LinkedHashMap<>();
    private final InMemoryRecommendationRepository recommendations;

    public InMemoryAreaRepository(InMemoryRecommendationRepository recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public Area save(Area area) {
        store.put(area.getId(), area);
        return area;
    }

    @Override
    public Optional<Area> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    @Override
    public boolean isReferencedByRecommendations(UUID areaId) {
        return recommendations.referencedAreaIds().contains(areaId);
    }

    @Override
    public List<Area> findAll(PageRequest pageRequest, String sortBy, String sortDirection) {
        Comparator<Area> comparator = "areaHectares".equals(sortBy)
                ? Comparator.comparing(a -> a.getAreaHectares().value())
                : Comparator.comparing(Area::getName);
        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        return store.values().stream()
                .sorted(comparator)
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
    }

    @Override
    public long count() {
        return store.size();
    }
}
