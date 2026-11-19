package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.repository.AppliedIrrigationRepository;
import com.irrigation.infrastructure.persistence.mapper.AppliedIrrigationPersistenceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link AppliedIrrigationRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class AppliedIrrigationRepositoryAdapter implements AppliedIrrigationRepository {

    private final AppliedIrrigationJpaRepository jpa;
    private final AppliedIrrigationPersistenceMapper mapper;

    public AppliedIrrigationRepositoryAdapter(AppliedIrrigationJpaRepository jpa,
                                              AppliedIrrigationPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public AppliedIrrigation upsert(AppliedIrrigation irrigation) {
        // INSERT ... ON CONFLICT atômico; a leitura seguinte, na mesma transação, devolve a linha persistida
        // (com o id existente, quando houve conflito).
        jpa.upsert(irrigation.getId(), irrigation.getAreaId(), irrigation.getDate(), irrigation.getDepth().value());
        return jpa.findByAreaIdAndIrrigationDate(irrigation.getAreaId(), irrigation.getDate())
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "Irrigação aplicada não encontrada após o upsert: área "
                                + irrigation.getAreaId() + ", data " + irrigation.getDate()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppliedIrrigation> findByAreaAndDate(UUID areaId, LocalDate date) {
        return jpa.findByAreaIdAndIrrigationDate(areaId, date).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppliedIrrigation> findByAreaAndPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return jpa.findByAreaIdAndIrrigationDateBetweenOrderByIrrigationDateAsc(areaId, from, to)
                .stream().map(mapper::toDomain).toList();
    }
}