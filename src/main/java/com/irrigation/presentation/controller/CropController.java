package com.irrigation.presentation.controller;

import com.irrigation.application.service.CropService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.dto.CropRequest;
import com.irrigation.presentation.dto.CropResponse;
import com.irrigation.presentation.dto.PagedResponse;
import com.irrigation.presentation.mapper.CropDtoMapper;
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
 * Endpoints de culturas e seus estágios (Req 2).
 */
@RestController
@RequestMapping("/api/v1/crops")
@Tag(name = "Culturas", description = "Cadastro de culturas com estágios de desenvolvimento e coeficiente de cultura (Kc)")
public class CropController {

    private final CropService service;
    private final CropDtoMapper mapper;

    public CropController(CropService service, CropDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Cria uma cultura com seus estágios")
    @ApiResponse(responseCode = "201", description = "Cultura criada")
    @BadRequestResponse
    public ResponseEntity<CropResponse> create(@Valid @RequestBody CropRequest request) {
        CropResponse body = mapper.toResponse(service.create(mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista culturas de forma paginada",
            description = "Padrão: página 0, tamanho 20 (máximo 100).")
    @ApiResponse(responseCode = "200", description = "Página de culturas (vazia se a página não existir)")
    @BadRequestResponse
    public PagedResponse<CropResponse> list(
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página, de 1 a 100") @RequestParam(required = false) Integer size) {
        return PagedResponse.from(service.list(page, size), mapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém uma cultura pelo identificador")
    @ApiResponse(responseCode = "200", description = "Cultura encontrada")
    @NotFoundResponse
    public CropResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma cultura (substituição completa, incluindo estágios)")
    @ApiResponse(responseCode = "200", description = "Cultura atualizada")
    @BadRequestResponse
    @NotFoundResponse
    public CropResponse update(@PathVariable UUID id, @Valid @RequestBody CropRequest request) {
        return mapper.toResponse(service.update(id, mapper.toInput(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma cultura")
    @ApiResponse(responseCode = "204", description = "Cultura excluída")
    @NotFoundResponse
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
