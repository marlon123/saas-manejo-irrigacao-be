package com.irrigation.domain.repository;

import com.irrigation.domain.model.SoilProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para agregados {@link SoilProfile}.
 * Implementada pela camada de infraestrutura com Spring Data JPA.
 */
public interface SoilProfileRepository {

    SoilProfile save(SoilProfile soilProfile);

    Optional<SoilProfile> findById(UUID id);

    void deleteById(UUID id);

    boolean existsById(UUID id);

    /**
     * Lista perfis de solo paginados. (Req 3.3)
     */
    List<SoilProfile> findAll(PageRequest pageRequest);

    long count();
}
