package com.irrigation.application.dto;

import com.irrigation.domain.model.ManagementPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação de saída de um plano de manejo. {@code endDate} é nula enquanto o plano está ativo.
 * {@code fFactor} é adimensional e {@code sprinklerRateMmPerHour} está em mm/h.
 */
public record ManagementPlanOutput(
        UUID id,
        UUID areaId,
        UUID cropId,
        UUID soilProfileId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal fFactor,
        BigDecimal sprinklerRateMmPerHour) {

    public static ManagementPlanOutput from(ManagementPlan plan) {
        return new ManagementPlanOutput(
                plan.getId(),
                plan.getAreaId(),
                plan.getCropId(),
                plan.getSoilProfileId(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getAvailabilityFactor().value(),
                plan.getSprinklerRate().value());
    }
}
