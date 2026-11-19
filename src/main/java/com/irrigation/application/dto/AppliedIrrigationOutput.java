package com.irrigation.application.dto;

import com.irrigation.domain.model.AppliedIrrigation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Representação de saída da irrigação real (IR) aplicada numa área e data, em mm. */
public record AppliedIrrigationOutput(
        UUID id,
        UUID areaId,
        LocalDate date,
        BigDecimal appliedIrrigationMm) {

    public static AppliedIrrigationOutput from(AppliedIrrigation irrigation) {
        return new AppliedIrrigationOutput(
                irrigation.getId(),
                irrigation.getAreaId(),
                irrigation.getDate(),
                irrigation.getDepth().value());
    }
}
