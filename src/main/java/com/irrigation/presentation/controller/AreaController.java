package com.irrigation.presentation.controller;

import com.irrigation.application.service.AreaService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.docs.UnprocessableEntityResponse;
import com.irrigation.presentation.dto.AreaRequest;
import com.irrigation.presentation.dto.AreaResponse;
import com.irrigation.presentation.dto.PagedResponse;
import com.irrigation.presentation.mapper.AreaDtoMapper;
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
 * Endpoints de áreas de cultivo (Req 1).
 */
@RestController
@RequestMapping("/api/v1/areas")
@Tag(name = "Áreas", description = "Cadastro e gerenciamento de áreas de cultivo (talhões)")
public class AreaController {

    private final AreaService service;
    private final AreaDtoMapper mapper;

    public AreaController(AreaService service, AreaDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Cria uma área de cultivo")
    @ApiResponse(responseCode = "201", description = "Área criada")
    @BadRequestResponse
    public ResponseEntity<AreaResponse> create(@Valid @RequestBody AreaRequest request) {
        AreaResponse body = mapper.toResponse(service.create(mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista áreas de forma paginada e ordenável",
            description = "Padrão: página 0, tamanho 20 (máximo 100). Ordenação por `name` ou `areaHectares`.")
    @ApiResponse(responseCode = "200", description = "Página de áreas (vazia se a página não existir)")
    @BadRequestResponse
    public PagedResponse<AreaResponse> list(
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página, de 1 a 100") @RequestParam(required = false) Integer size,
            @Parameter(description = "Campo de ordenação: name ou areaHectares") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Direção: asc ou desc") @RequestParam(required = false) String direction) {
        return PagedResponse.from(service.list(page, size, sortBy, direction), mapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém uma área pelo identificador")
    @ApiResponse(responseCode = "200", description = "Área encontrada")
    @NotFoundResponse
    public AreaResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma área (substituição completa)")
    @ApiResponse(responseCode = "200", description = "Área atualizada")
    @BadRequestResponse
    @NotFoundResponse
    public AreaResponse update(@PathVariable UUID id, @Valid @RequestBody AreaRequest request) {
        return mapper.toResponse(service.update(id, mapper.toInput(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui uma área",
            description = "Não é possível excluir uma área referenciada por recomendações de irrigação.")
    @ApiResponse(responseCode = "204", description = "Área excluída")
    @NotFoundResponse
    @UnprocessableEntityResponse
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
