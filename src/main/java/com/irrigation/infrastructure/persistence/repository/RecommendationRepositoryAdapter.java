package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.infrastructure.persistence.mapper.RecommendationPersistenceMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link RecommendationRepository}
 * com Spring Data JPA. Resultados ordenados do mais recente para o mais antigo.
 */
@Repository
@Transactional
public class RecommendationRepositoryAdapter implements RecommendationRepository {

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, "dateTime").and(Sort.by(Sort.Direction.ASC, "id"));

    private final RecommendationJpaRepository jpa;
    private final RecommendationPersistenceMapper mapper;

    public RecommendationRepositoryAdapter(RecommendationJpaRepository jpa, RecommendationPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Recommendation save(Recommendation recommendation) {
        return mapper.toDomain(jpa.save(mapper.toEntity(recommendation)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Recommendation> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recommendation> findByArea(UUID areaId, PageRequest pageRequest) {
        return jpa.findByAreaId(areaId, pageable(pageRequest)).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByArea(UUID areaId) {
        return jpa.countByAreaId(areaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recommendation> findByDateRange(LocalDateTime from, LocalDateTime to, PageRequest pageRequest) {
        return jpa.findByDateTimeBetween(from, to, pageable(pageRequest)).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDateRange(LocalDateTime from, LocalDateTime to) {
        return jpa.countByDateTimeBetween(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recommendation> findByAreaAndDateRange(
            UUID areaId, LocalDateTime from, LocalDateTime to, PageRequest pageRequest) {
        return jpa.findByAreaIdAndDateTimeBetween(areaId, from, to, pageable(pageRequest))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByAreaAndDateRange(UUID areaId, LocalDateTime from, LocalDateTime to) {
        return jpa.countByAreaIdAndDateTimeBetween(areaId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }

    private Pageable pageable(PageRequest pageRequest) {
        return org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), DEFAULT_SORT);
    }
}
