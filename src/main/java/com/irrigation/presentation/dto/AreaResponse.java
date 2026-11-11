package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.IrrigationSystemType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representação de uma área retornada pela API.
 */
public record AreaResponse(
        UUID id,
        String name,
        BigDecimal areaHectares,
        BigDecimal latitude,
        BigDecimal longitude,
        String generalCharacteristics,
        IrrigationSystemType irrigationSystemType,
        BigDecimal systemFlowRate,
        BigDecimal irrigationEfficiency) {
}
