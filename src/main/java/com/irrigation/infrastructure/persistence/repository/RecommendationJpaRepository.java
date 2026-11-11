package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.RecommendationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RecommendationJpaRepository extends JpaRepository<RecommendationEntity, UUID> {

    List<RecommendationEntity> findByAreaId(UUID areaId, Pageable pageable);

    long countByAreaId(UUID areaId);

    List<RecommendationEntity> findByDateTimeBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByDateTimeBetween(LocalDateTime from, LocalDateTime to);

    List<RecommendationEntity> findByAreaIdAndDateTimeBetween(
            UUID areaId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByAreaIdAndDateTimeBetween(UUID areaId, LocalDateTime from, LocalDateTime to);
}
