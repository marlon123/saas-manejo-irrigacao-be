package com.irrigation.presentation.dto;

import com.irrigation.domain.model.enums.IrrigationSystemType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Corpo de requisição para criar ou atualizar uma área (Req 1.1, 1.8 da spec original; Req 3.1 do módulo
 * de balanço hídrico).
 *
 * <p>Somente o nome é obrigatório. Área em hectares, tipo de sistema e vazão são opcionais e, quando
 * presentes, seguem validados. O Bean Validation é a primeira barreira; as regras autoritativas vivem no domínio.
 */
public record AreaRequest(
        @NotBlank @Size(max = 255)
        String name,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000.00")
        BigDecimal areaHectares,
        @DecimalMin("-90") @DecimalMax("90")
        BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180")
        BigDecimal longitude,
        String generalCharacteristics,
        IrrigationSystemType irrigationSystemType,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000.00")
        BigDecimal systemFlowRate,
        @DecimalMin("0.01") @DecimalMax("1")
        BigDecimal irrigationEfficiency) {
}