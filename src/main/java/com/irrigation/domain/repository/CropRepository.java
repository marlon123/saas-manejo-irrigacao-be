package com.irrigation.domain.repository;

import com.irrigation.domain.model.Crop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para agregados {@link Crop}.
 * Implementada pela camada de infraestrutura com Spring Data JPA.
 */
public interface CropRepository {

    Crop save(Crop crop);

    Optional<Crop> findById(UUID id);

    void deleteById(UUID id);

    boolean existsById(UUID id);

    /**
     * Lista culturas paginadas. (Req 2.3–2.4)
     */
    List<Crop> findAll(PageRequest pageRequest);

    long count();
}
