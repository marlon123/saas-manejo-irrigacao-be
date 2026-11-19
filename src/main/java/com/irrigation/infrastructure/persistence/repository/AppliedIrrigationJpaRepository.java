package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.AppliedIrrigationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppliedIrrigationJpaRepository extends JpaRepository<AppliedIrrigationEntity, UUID> {

    Optional<AppliedIrrigationEntity> findByAreaIdAndIrrigationDate(UUID areaId, LocalDate irrigationDate);

    /** Registros da área no período (inclusivo), em ordem crescente de data. */
    List<AppliedIrrigationEntity> findByAreaIdAndIrrigationDateBetweenOrderByIrrigationDateAsc(
            UUID areaId, LocalDate from, LocalDate to);

    /**
     * Insere ou substitui, de forma atômica, a linha da área na data. Em conflito mantém o
     * {@code id} existente e atualiza o valor. {@code updated_at} fica a cargo do trigger.
     * O contexto de persistência é limpo para que a leitura seguinte veja a linha gravada.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO applied_irrigation (id, area_id, irrigation_date, applied_irrigation_mm)
            VALUES (:id, :areaId, :irrigationDate, :appliedIrrigationMm)
            ON CONFLICT (area_id, irrigation_date) DO UPDATE
               SET applied_irrigation_mm = EXCLUDED.applied_irrigation_mm
            """, nativeQuery = true)
    int upsert(@Param("id") UUID id,
               @Param("areaId") UUID areaId,
               @Param("irrigationDate") LocalDate irrigationDate,
               @Param("appliedIrrigationMm") BigDecimal appliedIrrigationMm);
}
