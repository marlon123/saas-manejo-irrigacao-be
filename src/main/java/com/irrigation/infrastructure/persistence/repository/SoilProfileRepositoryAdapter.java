package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.infrastructure.persistence.mapper.SoilProfilePersistenceMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link SoilProfileRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class SoilProfileRepositoryAdapter implements SoilProfileRepository {

    private final SoilProfileJpaRepository jpa;
    private final SoilProfilePersistenceMapper mapper;

    public SoilProfileRepositoryAdapter(SoilProfileJpaRepository jpa, SoilProfilePersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public SoilProfile save(SoilProfile soilProfile) {
        return mapper.toDomain(jpa.save(mapper.toEntity(soilProfile)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SoilProfile> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SoilProfile> findAll(PageRequest pageRequest) {
        Sort sort = Sort.by(Sort.Direction.ASC, "soilType").and(Sort.by(Sort.Direction.ASC, "id"));
        return jpa.findAll(org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), sort))
                .getContent().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }
}
