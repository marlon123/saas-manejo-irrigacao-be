package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.infrastructure.persistence.entity.AreaEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link AreaEntity} (persistência) e {@link Area} (domínio).
 */
@Component
public class AreaPersistenceMapper {

    public Area toDomain(AreaEntity entity) {
        GeographicLocation location = (entity.getLatitude() != null && entity.getLongitude() != null)
                ? new GeographicLocation(entity.getLatitude(), entity.getLongitude())
                : null;
        IrrigationEfficiency efficiency = entity.getIrrigationEfficiency() != null
                ? new IrrigationEfficiency(entity.getIrrigationEfficiency())
                : null;

        return new Area(
                entity.getId(),
                entity.getName(),
                new AreaInHectares(entity.getAreaHectares()),
                location,
                entity.getGeneralCharacteristics(),
                entity.getIrrigationSystemType(),
                new FlowRate(entity.getSystemFlowRate()),
                efficiency);
    }

    public AreaEntity toEntity(Area area) {
        AreaEntity entity = AreaEntity.newInstance();
        entity.setId(area.getId());
        entity.setName(area.getName());
        entity.setAreaHectares(area.getAreaHectares().value());
        if (area.getGeographicLocation() != null) {
            entity.setLatitude(area.getGeographicLocation().latitude());
            entity.setLongitude(area.getGeographicLocation().longitude());
        }
        entity.setGeneralCharacteristics(area.getGeneralCharacteristics());
        entity.setIrrigationSystemType(area.getIrrigationSystemType());
        entity.setSystemFlowRate(area.getSystemFlowRate().value());
        if (area.getIrrigationEfficiency() != null) {
            entity.setIrrigationEfficiency(area.getIrrigationEfficiency().value());
        }
        return entity;
    }
}
