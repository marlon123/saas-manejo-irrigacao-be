package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.infrastructure.persistence.entity.SoilProfileEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link SoilProfileEntity} (persistência) e {@link SoilProfile} (domínio).
 */
@Component
public class SoilProfilePersistenceMapper {

    public SoilProfile toDomain(SoilProfileEntity entity) {
        return new SoilProfile(
                entity.getId(),
                entity.getSoilType(),
                new VolumetricWaterContent(entity.getFieldCapacity()),
                new VolumetricWaterContent(entity.getPermanentWiltingPoint()),
                new BulkDensity(entity.getBulkDensity()),
                new RootDepth(entity.getEffectiveRootDepth()),
                new AvailableWater(entity.getAvailableWater()),
                entity.getSoilWaterUnit(),
                entity.getBulkDensityUnit(),
                entity.getRootDepthUnit(),
                entity.getAvailableWaterUnit());
    }

    public SoilProfileEntity toEntity(SoilProfile soil) {
        SoilProfileEntity entity = SoilProfileEntity.newInstance();
        entity.setId(soil.getId());
        entity.setSoilType(soil.getSoilType());
        entity.setFieldCapacity(soil.getFieldCapacity().value());
        entity.setPermanentWiltingPoint(soil.getPermanentWiltingPoint().value());
        entity.setBulkDensity(soil.getBulkDensity().value());
        entity.setEffectiveRootDepth(soil.getEffectiveRootDepth().value());
        entity.setAvailableWater(soil.getAvailableWater().value());
        entity.setSoilWaterUnit(soil.getSoilWaterUnit());
        entity.setBulkDensityUnit(soil.getBulkDensityUnit());
        entity.setRootDepthUnit(soil.getRootDepthUnit());
        entity.setAvailableWaterUnit(soil.getAvailableWaterUnit());
        return entity;
    }
}
