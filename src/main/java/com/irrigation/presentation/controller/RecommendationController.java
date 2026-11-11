package com.irrigation.presentation.controller;

import com.irrigation.application.service.RecommendationService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.docs.UnprocessableEntityResponse;
import com.irrigation.presentation.dto.CalculateRecommendationRequest;
import com.irrigation.presentation.dto.PagedResponse;
import com.irrigation.presentation.dto.RecommendationResponse;
import com.irrigation.presentation.mapper.RecommendationDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Endpoints de cálculo e histórico de recomendações (Req 5).
 * Não há atualização nem exclusão: recomendações são imutáveis (Req 5.7).
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recomendações", description = "Cálculo de irrigação e histórico imutável de recomendações")
public class RecommendationController {

    private final RecommendationService service;
    private final RecommendationDtoMapper mapper;

    public RecommendationController(RecommendationService service, RecommendationDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calcula e armazena uma recomendação de irrigação",
            description = "Executa a metodologia informada e grava o resultado como registro imutável. "
                    + "Sem fórmulas registradas, responde 422 (metodologia indisponível).")
    @ApiResponse(responseCode = "201", description = "Recomendação calculada e armazenada")
    @BadRequestResponse
    @NotFoundResponse
    @UnprocessableEntityResponse
    public ResponseEntity<RecommendationResponse> calculate(@Valid @RequestBody CalculateRecommendationRequest request) {
        RecommendationResponse body = mapper.toResponse(service.calculate(mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/recommendations/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "Lista o histórico de recomendações",
            description = "Filtros opcionais por área e por período (ISO-8601, UTC). Mais recentes primeiro.")
    @ApiResponse(responseCode = "200", description = "Página de recomendações (vazia se nada corresponder)")
    @BadRequestResponse
    public PagedResponse<RecommendationResponse> list(
            @Parameter(description = "Filtra por área") @RequestParam(required = false) UUID areaId,
            @Parameter(description = "Início do período (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fim do período (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página, de 1 a 100") @RequestParam(required = false) Integer size) {
        return PagedResponse.from(service.list(areaId, dateFrom, dateTo, page, size), mapper::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtém uma recomendação pelo identificador")
    @ApiResponse(responseCode = "200", description = "Recomendação encontrada")
    @NotFoundResponse
    public RecommendationResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.getById(id));
    }
}
