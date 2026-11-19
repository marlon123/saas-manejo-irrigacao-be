package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Corpo de requisição para criar ou atualizar uma cultura (Req 2.1, 2.7 da spec original; Req 2 do módulo
 * de balanço hídrico).
 *
 * <p>{@code kc} e {@code rootDepthMm} são obrigatórios (Req 2.10). A lista de estágios é opcional e aceita
 * de 0 a 20 itens (Req 2.4, 2.5).
 */
public record CropRequest(
        @NotBlank @Size(max = 255)
        String name,
        @Size(max = 255)
        String variety,
        Integer cultivationCycleDays,
        @Size(max = 20) @Valid
        List<CropStageRequest> stages,
        @Schema(description = "Coeficiente de cultura Kc (adimensional), maior que 0 e até 2,0, com até 2 casas decimais",
                example = "1.15")
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("2.0")
        BigDecimal kc,
        @Schema(description = "Profundidade radicular Z em milímetros, maior que 0 e até 5000, com até 2 casas decimais",
                example = "400")
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000")
        BigDecimal rootDepthMm) {

    /** Construtor de compatibilidade (assinatura anterior ao módulo): {@code kc} e {@code rootDepthMm} nulos. */
    public CropRequest(String name, String variety, Integer cultivationCycleDays, List<CropStageRequest> stages) {
        this(name, variety, cultivationCycleDays, stages, null, null);
    }
}