package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.Crop;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.infrastructure.persistence.mapper.CropPersistenceMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link CropRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class CropRepositoryAdapter implements CropRepository {

    private final CropJpaRepository jpa;
    private final CropPersistenceMapper mapper;

    public CropRepositoryAdapter(CropJpaRepository jpa, CropPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Crop save(Crop crop) {
        return mapper.toDomain(jpa.save(mapper.toEntity(crop)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Crop> findById(UUID id) {
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
    public List<Crop> findAll(PageRequest pageRequest) {
        Sort sort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        return jpa.findAll(org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), sort))
                .getContent().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }
}
