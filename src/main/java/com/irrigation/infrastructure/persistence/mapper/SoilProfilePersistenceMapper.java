package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.infrastructure.persistence.entity.SoilProfileEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Converte entre {@link SoilProfileEntity} (persistência) e {@link SoilProfile} (domínio).
 * Campos opcionais (V4) são mapeados de nulo para nulo nos dois sentidos.
 */
@Component
public class SoilProfilePersistenceMapper {

    public SoilProfile toDomain(SoilProfileEntity entity) {
        return new SoilProfile(
                entity.getId(),
                entity.getSoilType(),
                wrap(entity.getFieldCapacity(), VolumetricWaterContent::new),
                wrap(entity.getPermanentWiltingPoint(), VolumetricWaterContent::new),
                wrap(entity.getFieldCapacityGravimetricPct(), GravimetricWaterContent::new),
                wrap(entity.getPermanentWiltingPointGravimetricPct(), GravimetricWaterContent::new),
                new BulkDensity(entity.getBulkDensity()),
                wrap(entity.getEffectiveRootDepth(), RootDepth::new),
                wrap(entity.getAvailableWater(), AvailableWater::new),
                entity.getSoilWaterUnit(),
                entity.getBulkDensityUnit(),
                entity.getRootDepthUnit(),
                entity.getAvailableWaterUnit());
    }

    public SoilProfileEntity toEntity(SoilProfile soil) {
        SoilProfileEntity entity = SoilProfileEntity.newInstance();
        entity.setId(soil.getId());
        entity.setSoilType(soil.getSoilType());
        entity.setFieldCapacity(value(soil.getFieldCapacity(), VolumetricWaterContent::value));
        entity.setPermanentWiltingPoint(value(soil.getPermanentWiltingPoint(), VolumetricWaterContent::value));
        entity.setFieldCapacityGravimetricPct(value(soil.getFieldCapacityGravimetric(), GravimetricWaterContent::value));
        entity.setPermanentWiltingPointGravimetricPct(
                value(soil.getPermanentWiltingPointGravimetric(), GravimetricWaterContent::value));
        entity.setBulkDensity(soil.getBulkDensity().value());
        entity.setEffectiveRootDepth(value(soil.getEffectiveRootDepth(), RootDepth::value));
        entity.setAvailableWater(value(soil.getAvailableWater(), AvailableWater::value));
        entity.setSoilWaterUnit(soil.getSoilWaterUnit());
        entity.setBulkDensityUnit(soil.getBulkDensityUnit());
        entity.setRootDepthUnit(soil.getRootDepthUnit());
        entity.setAvailableWaterUnit(soil.getAvailableWaterUnit());
        return entity;
    }

    private static <T> T wrap(BigDecimal raw, Function<BigDecimal, T> factory) {
        return raw == null ? null : factory.apply(raw);
    }

    private static <T> BigDecimal value(T valueObject, Function<T, BigDecimal> accessor) {
        return valueObject == null ? null : accessor.apply(valueObject);
    }
}