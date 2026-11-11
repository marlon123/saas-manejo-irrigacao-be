package com.irrigation.presentation.controller;

import com.irrigation.application.service.SoilProfileService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.dto.PagedResponse;
import com.irrigation.presentation.dto.SoilProfileRequest;
import com.irrigation.presentation.dto.SoilProfileResponse;
import com.irrigation.presentation.mapper.SoilProfileDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Endpoints de perfis de solo (Req 3).
 */
@RestController
@RequestMapping("/api/v1/soil-profiles")
@Tag(name = "Perfis de solo", description = "Parâmetros de solo com unidades de medida explícitas")
public class SoilProfileController {

    private final SoilProfileService service;
    private final SoilProfileDtoMapper mapper;

    public SoilProfileController(SoilProfileService service, SoilProfileDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Cria um perfil de solo")
    @ApiResponse(responseCode = "201", description = "Perfil de solo criado")
    @BadRequestResponse
    public ResponseEntity<SoilProfileResponse> create(@Valid @RequestBody SoilProfileRequest request) {
        SoilProfileResponse body = mapper.toResponse(service.create(mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista perfis de solo de forma paginada",
            description = "Padrão: página 0, tamanho 20 (máximo 100).")
    @ApiResponse(responseCode = "200", description = "Página de perfis de solo (vazia se a página não existir)")
    @BadRequestResponse
    public PagedResponse<SoilProfileResponse> list(
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página, de 1 a 100") @RequestParam(required = false) Integer size) {
        return PagedResponse.from(service.list(page, size), mapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém um perfil de solo pelo identificador")
    @ApiResponse(responseCode = "200", description = "Perfil de solo encontrado")
    @NotFoundResponse
    public SoilProfileResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um perfil de solo (substituição completa)")
    @ApiResponse(responseCode = "200", description = "Perfil de solo atualizado")
    @BadRequestResponse
    @NotFoundResponse
    public SoilProfileResponse update(@PathVariable UUID id, @Valid @RequestBody SoilProfileRequest request) {
        return mapper.toResponse(service.update(id, mapper.toInput(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um perfil de solo")
    @ApiResponse(responseCode = "204", description = "Perfil de solo excluído")
    @NotFoundResponse
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
