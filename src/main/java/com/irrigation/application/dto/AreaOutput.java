package com.irrigation.application.dto;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.enums.IrrigationSystemType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de saída de uma área.
 */
public record AreaOutput(
        UUID id,
        String name,
        BigDecimal areaHectares,
        BigDecimal latitude,
        BigDecimal longitude,
        String generalCharacteristics,
        IrrigationSystemType irrigationSystemType,
        BigDecimal systemFlowRate,
        BigDecimal irrigationEfficiency) {

    public static AreaOutput from(Area area) {
        return new AreaOutput(
                area.getId(),
                area.getName(),
                area.getAreaHectares().value(),
                area.getGeographicLocation() != null ? area.getGeographicLocation().latitude() : null,
                area.getGeographicLocation() != null ? area.getGeographicLocation().longitude() : null,
                area.getGeneralCharacteristics(),
                area.getIrrigationSystemType(),
                area.getSystemFlowRate().value(),
                area.getIrrigationEfficiency() != null ? area.getIrrigationEfficiency().value() : null);
    }
}
