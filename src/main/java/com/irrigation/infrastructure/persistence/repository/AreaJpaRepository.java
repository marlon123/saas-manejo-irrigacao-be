package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.AreaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AreaJpaRepository extends JpaRepository<AreaEntity, UUID> {

    /**
     * Ordena por área em hectares, crescente, com nulos no fim. A ordenação é escrita na consulta porque
     * o Spring Data ignora {@code nullsLast} no {@code findAll(Pageable)}: em ordem decrescente o PostgreSQL
     * colocaria os nulos primeiro. O {@code id} desempata para a paginação ser estável. Usar com {@code Pageable} sem ordenação.
     */
    @Query(value = "select a from AreaEntity a order by a.areaHectares asc nulls last, a.id asc",
            countQuery = "select count(a) from AreaEntity a")
    Page<AreaEntity> findAllOrderByAreaHectaresAscNullsLast(Pageable pageable);

    /** Igual a {@link #findAllOrderByAreaHectaresAscNullsLast}, em ordem decrescente e ainda com nulos no fim. */
    @Query(value = "select a from AreaEntity a order by a.areaHectares desc nulls last, a.id asc",
            countQuery = "select count(a) from AreaEntity a")
    Page<AreaEntity> findAllOrderByAreaHectaresDescNullsLast(Pageable pageable);

    /**
     * Indica se alguma recomendação referencia a área (Req 1.19).
     */
    @Query("select count(r) > 0 from RecommendationEntity r where r.areaId = :areaId")
    boolean isReferencedByRecommendations(@Param("areaId") UUID areaId);
}
