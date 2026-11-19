package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.ManagementPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagementPlanJpaRepository extends JpaRepository<ManagementPlanEntity, UUID> {

    /** Planos da área, do início mais recente para o mais antigo. */
    List<ManagementPlanEntity> findByAreaIdOrderByStartDateDesc(UUID areaId);

    /** Plano ativo (sem data final) da área. Há no máximo um, garantido por índice único parcial. */
    Optional<ManagementPlanEntity> findByAreaIdAndEndDateIsNull(UUID areaId);

    /** Plano que cobre a data: início menor ou igual e data final nula ou maior ou igual. */
    @Query("""
            select p from ManagementPlanEntity p
            where p.areaId = :areaId
              and p.startDate <= :date
              and (p.endDate is null or p.endDate >= :date)
            order by p.startDate desc
            """)
    List<ManagementPlanEntity> findCoveringDate(@Param("areaId") UUID areaId, @Param("date") LocalDate date);
}
