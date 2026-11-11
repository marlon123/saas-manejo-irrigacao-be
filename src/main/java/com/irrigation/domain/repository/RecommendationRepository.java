package com.irrigation.domain.repository;

import com.irrigation.domain.model.Recommendation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para agregados {@link Recommendation}.
 * Implementada pela camada de infraestrutura com Spring Data JPA.
 */
public interface RecommendationRepository {

    Recommendation save(Recommendation recommendation);

    Optional<Recommendation> findById(UUID id);

    /**
     * Filtra recomendações por área. (Req 5.2)
     */
    List<Recommendation> findByArea(UUID areaId, PageRequest pageRequest);

    long countByArea(UUID areaId);

    /**
     * Filtra recomendações por intervalo de datas. (Req 5.3)
     */
    List<Recommendation> findByDateRange(LocalDateTime from, LocalDateTime to, PageRequest pageRequest);

    long countByDateRange(LocalDateTime from, LocalDateTime to);

    /**
     * Filtra recomendações por área e intervalo de datas. (Req 5.4)
     */
    List<Recommendation> findByAreaAndDateRange(UUID areaId, LocalDateTime from, LocalDateTime to, PageRequest pageRequest);

    long countByAreaAndDateRange(UUID areaId, LocalDateTime from, LocalDateTime to);

    long count();
}
