package com.irrigation.application.dto;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.enums.IrrigationSystemType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de saída de uma área. Área em hectares, tipo de sistema e vazão são nulos quando não
 * informados (Req 3.1).
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
                area.getAreaHectares() == null ? null : area.getAreaHectares().value(),
                area.getGeographicLocation() != null ? area.getGeographicLocation().latitude() : null,
                area.getGeographicLocation() != null ? area.getGeographicLocation().longitude() : null,
                area.getGeneralCharacteristics(),
                area.getIrrigationSystemType(),
                area.getSystemFlowRate() == null ? null : area.getSystemFlowRate().value(),
                area.getIrrigationEfficiency() != null ? area.getIrrigationEfficiency().value() : null);
    }
}
