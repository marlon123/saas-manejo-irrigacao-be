package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.AreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AreaJpaRepository extends JpaRepository<AreaEntity, UUID> {

    /**
     * Indica se alguma recomendação referencia a área (Req 1.19).
     */
    @Query("select count(r) > 0 from RecommendationEntity r where r.areaId = :areaId")
    boolean isReferencedByRecommendations(@Param("areaId") UUID areaId);
}
