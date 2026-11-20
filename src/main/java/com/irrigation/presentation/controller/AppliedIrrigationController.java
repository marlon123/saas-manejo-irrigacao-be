package com.irrigation.presentation.controller;

import com.irrigation.application.service.AppliedIrrigationService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.dto.AppliedIrrigationRequest;
import com.irrigation.presentation.dto.AppliedIrrigationResponse;
import com.irrigation.presentation.mapper.AppliedIrrigationDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints da Irrigação Real (IR) diária de uma área (Req 6).
 */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/applied-irrigation")
@Tag(name = "Irrigação real", description = "Lâmina de irrigação efetivamente aplicada por dia e área")
public class AppliedIrrigationController {

    private final AppliedIrrigationService service;
    private final AppliedIrrigationDtoMapper mapper;

    public AppliedIrrigationController(AppliedIrrigationService service, AppliedIrrigationDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Registra a irrigação real de um dia (upsert)",
            description = "A data vai no corpo. Se já existir registro para a área e a data, ele é substituído. "
                    + "Responde 200 com o registro persistido.")
    @ApiResponse(responseCode = "200", description = "Registro gravado")
    @BadRequestResponse
    @NotFoundResponse
    public AppliedIrrigationResponse upsert(
            @PathVariable UUID areaId, @Valid @RequestBody AppliedIrrigationRequest request) {
        return mapper.toResponse(service.upsert(areaId, mapper.toInput(request)));
    }

    @GetMapping
    @Operation(summary = "Lista a irrigação real de um período",
            description = "Período inclusivo, de até 366 dias, com datas no formato yyyy-MM-dd.")
    @ApiResponse(responseCode = "200", description = "Registros do período (lista vazia se não houver)")
    @BadRequestResponse
    @NotFoundResponse
    public List<AppliedIrrigationResponse> list(
            @PathVariable UUID areaId,
            @Parameter(description = "Data inicial (yyyy-MM-dd)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final (yyyy-MM-dd)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.findByPeriod(areaId, from, to).stream().map(mapper::toResponse).toList();
    }
}
