package com.irrigation.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de requisição para criar ou atualizar uma cultura (Req 2.1, 2.7).
 */
public record CropRequest(
        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 255)
        String variety,

        Integer cultivationCycleDays,

        @NotEmpty @Size(max = 20) @Valid
        List<CropStageRequest> stages) {
}
