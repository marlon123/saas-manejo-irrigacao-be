package com.irrigation.domain.repository;

import com.irrigation.domain.model.Area;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para agregados {@link Area}.
 * Implementada pela camada de infraestrutura com Spring Data JPA.
 */
public interface AreaRepository {

    Area save(Area area);

    Optional<Area> findById(UUID id);

    void deleteById(UUID id);

    boolean existsById(UUID id);

    /**
     * Verifica se a área é referenciada por recomendações.
     * (Req 1.19 — bloqueia exclusão se houver referências)
     */
    boolean isReferencedByRecommendations(UUID areaId);

    /**
     * Lista áreas paginadas e ordenadas.
     * (Req 1.3–1.5) — sortable fields: name, areaHectares
     */
    List<Area> findAll(PageRequest pageRequest, String sortBy, String sortDirection);

    long count();
}
