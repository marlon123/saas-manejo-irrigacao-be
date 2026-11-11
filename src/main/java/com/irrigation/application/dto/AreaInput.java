package com.irrigation.application.dto;

import com.irrigation.domain.model.enums.IrrigationSystemType;

import java.math.BigDecimal;

/**
 * Dados de entrada para criar ou atualizar uma área (Req 1.1, 1.8).
 * Latitude e longitude são opcionais, mas devem ser informadas em conjunto (Req 1.16).
 */
public record AreaInput(
        String name,
        BigDecimal areaHectares,
        BigDecimal latitude,
        BigDecimal longitude,
        String generalCharacteristics,
        IrrigationSystemType irrigationSystemType,
        BigDecimal systemFlowRate,
        BigDecimal irrigationEfficiency) {
}
