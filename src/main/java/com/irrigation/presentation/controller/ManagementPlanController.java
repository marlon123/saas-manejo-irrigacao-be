package com.irrigation.presentation.controller;

import com.irrigation.application.service.ManagementPlanService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.docs.UnprocessableEntityResponse;
import com.irrigation.presentation.dto.ManagementPlanRequest;
import com.irrigation.presentation.dto.ManagementPlanResponse;
import com.irrigation.presentation.mapper.ManagementPlanDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de planos de manejo de uma área (Req 4).
 */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/management-plans")
@Tag(name = "Planos de manejo", description = "Plano de manejo da área: cultura, solo, fator F e taxa do aspersor")
public class ManagementPlanController {

    private final ManagementPlanService service;
    private final ManagementPlanDtoMapper mapper;

    public ManagementPlanController(ManagementPlanService service, ManagementPlanDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Cria um plano de manejo para a área",
            description = "Cada área tem no máximo um plano ativo e os períodos dos planos não se sobrepõem.")
    @ApiResponse(responseCode = "201", description = "Plano criado")
    @BadRequestResponse
    @NotFoundResponse
    @UnprocessableEntityResponse
    public ResponseEntity<ManagementPlanResponse> create(
            @PathVariable UUID areaId, @Valid @RequestBody ManagementPlanRequest request) {
        ManagementPlanResponse body = mapper.toResponse(service.create(areaId, mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{planId}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista os planos de manejo da área",
            description = "Plano ativo e encerrados, por data de início decrescente. Lista vazia se não houver planos.")
    @ApiResponse(responseCode = "200", description = "Planos da área")
    @NotFoundResponse
    public List<ManagementPlanResponse> list(@PathVariable UUID areaId) {
        return service.listByArea(areaId).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Obtém um plano de manejo da área",
            description = "Responde 404 se o plano não existir ou pertencer a outra área.")
    @ApiResponse(responseCode = "200", description = "Plano encontrado")
    @NotFoundResponse
    public ManagementPlanResponse get(@PathVariable UUID areaId, @PathVariable UUID planId) {
        return mapper.toResponse(service.getById(areaId, planId));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Atualiza um plano de manejo (fator F, taxa e data final)",
            description = "A data de início não muda. Cultura e solo só podem ser trocados se o plano não tiver balanços.")
    @ApiResponse(responseCode = "200", description = "Plano atualizado")
    @BadRequestResponse
    @NotFoundResponse
    @UnprocessableEntityResponse
    public ManagementPlanResponse update(
            @PathVariable UUID areaId, @PathVariable UUID planId, @Valid @RequestBody ManagementPlanRequest request) {
        return mapper.toResponse(service.update(areaId, planId, mapper.toInput(request)));
    }
}
