package com.irrigation.domain.repository;

import com.irrigation.domain.model.AppliedIrrigation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para {@link AppliedIrrigation} (irrigação aplicada diária).
 * Há no máximo um registro por área e data.
 */
public interface AppliedIrrigationRepository {

    /**
     * Insere ou substitui, de forma atômica, o registro da área na data.
     * Devolve o registro persistido.
     */
    AppliedIrrigation upsert(AppliedIrrigation irrigation);

    Optional<AppliedIrrigation> findByAreaAndDate(UUID areaId, LocalDate date);

    /** Registros da área no período (inclusivo), em ordem crescente de data. */
    List<AppliedIrrigation> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to);
}
