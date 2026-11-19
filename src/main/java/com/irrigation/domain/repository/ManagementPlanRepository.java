package com.irrigation.domain.repository;

import com.irrigation.domain.model.ManagementPlan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para agregados {@link ManagementPlan}.
 * Implementada pela camada de infraestrutura com Spring Data JPA.
 */
public interface ManagementPlanRepository {

    ManagementPlan save(ManagementPlan plan);

    Optional<ManagementPlan> findById(UUID id);

    /** Lista os planos da área, do mais recente para o mais antigo (por data de início). */
    List<ManagementPlan> findByAreaOrderByStartDateDesc(UUID areaId);

    /** Plano ativo (sem data final) da área, se existir. */
    Optional<ManagementPlan> findActiveByArea(UUID areaId);

    /**
     * Plano da área que cobre a data: início menor ou igual à data e
     * data final nula ou maior ou igual à data.
     */
    Optional<ManagementPlan> findCoveringDate(UUID areaId, LocalDate date);

    /**
     * Indica se o plano possui balanços diários calculados.
     * <p>
     * A tabela de balanços só existe a partir da Fase 4. Enquanto isso, o adaptador
     * da Fase 3 devolve sempre {@code false}.
     */
    boolean hasBalances(UUID planId);

    /**
     * Data do último balanço calculado do plano.
     * <p>
     * A tabela de balanços só existe a partir da Fase 4. Enquanto isso, o adaptador
     * da Fase 3 devolve sempre {@link Optional#empty()}.
     */
    Optional<LocalDate> lastCalculatedDate(UUID planId);
}
