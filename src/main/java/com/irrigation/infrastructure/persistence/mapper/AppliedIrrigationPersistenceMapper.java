package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.infrastructure.persistence.entity.AppliedIrrigationEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link AppliedIrrigationEntity} (persistência) e {@link AppliedIrrigation} (domínio).
 */
@Component
public class AppliedIrrigationPersistenceMapper {

    public AppliedIrrigation toDomain(AppliedIrrigationEntity entity) {
        return new AppliedIrrigation(
                entity.getId(),
                entity.getAreaId(),
                entity.getIrrigationDate(),
                new AppliedIrrigationDepth(entity.getAppliedIrrigationMm()));
    }

    public AppliedIrrigationEntity toEntity(AppliedIrrigation irrigation) {
        AppliedIrrigationEntity entity = AppliedIrrigationEntity.newInstance();
        entity.setId(irrigation.getId());
        entity.setAreaId(irrigation.getAreaId());
        entity.setIrrigationDate(irrigation.getDate());
        entity.setAppliedIrrigationMm(irrigation.getDepth().value());
        return entity;
    }
}
