package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.infrastructure.persistence.entity.ManagementPlanEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link ManagementPlanEntity} (persistência) e {@link ManagementPlan} (domínio).
 */
@Component
public class ManagementPlanPersistenceMapper {

    public ManagementPlan toDomain(ManagementPlanEntity entity) {
        return new ManagementPlan(
                entity.getId(),
                entity.getAreaId(),
                entity.getCropId(),
                entity.getSoilProfileId(),
                entity.getStartDate(),
                entity.getEndDate(),
                new AvailabilityFactor(entity.getAvailabilityFactor()),
                new SprinklerRate(entity.getSprinklerRate()));
    }

    public ManagementPlanEntity toEntity(ManagementPlan plan) {
        ManagementPlanEntity entity = ManagementPlanEntity.newInstance();
        entity.setId(plan.getId());
        entity.setAreaId(plan.getAreaId());
        entity.setCropId(plan.getCropId());
        entity.setSoilProfileId(plan.getSoilProfileId());
        entity.setStartDate(plan.getStartDate());
        entity.setEndDate(plan.getEndDate());
        entity.setAvailabilityFactor(plan.getAvailabilityFactor().value());
        entity.setSprinklerRate(plan.getSprinklerRate().value());
        return entity;
    }
}
