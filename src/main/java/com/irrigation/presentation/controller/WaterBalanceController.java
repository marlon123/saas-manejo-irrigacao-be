package com.irrigation.presentation.controller;

import com.irrigation.application.service.WaterBalanceQueryService;
import com.irrigation.application.service.WaterBalanceService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.ConflictResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.docs.UnprocessableEntityResponse;
import com.irrigation.presentation.dto.CalculateWaterBalanceRequest;
import com.irrigation.presentation.dto.DailyWaterBalanceResponse;
import com.irrigation.presentation.dto.PagedResponse;
import com.irrigation.presentation.dto.ReprocessWaterBalanceRequest;
import com.irrigation.presentation.mapper.WaterBalanceDtoMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints do balanço hídrico diário de uma área (Req 7, 13 e 14).
 */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/water-balance")
@Tag(name = "Balanço hídrico", description = "Cálculo, reprocessamento e consulta do balanço hídrico diário")
public class WaterBalanceController {

    private final WaterBalanceService service;
    private final WaterBalanceQueryService queryService;
    private final WaterBalanceDtoMapper mapper;

    public WaterBalanceController(WaterBalanceService service, WaterBalanceQueryService queryService,
                                  WaterBalanceDtoMapper mapper) {
        this.service = service;
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calcula o balanço hídrico de um dia",
            description = "Exige meteorologia, irrigação real, plano de manejo e dados de solo e cultura. "
                    + "Os dias anteriores devem estar calculados. Responde 409 se já há processamento na área.")
    @ApiResponse(responseCode = "201", description = "Balanço calculado e gravado")
    @BadRequestResponse
    @NotFoundResponse
    @ConflictResponse
    @UnprocessableEntityResponse
    public ResponseEntity<DailyWaterBalanceResponse> calculate(
            @PathVariable UUID areaId, @Valid @RequestBody CalculateWaterBalanceRequest request) {
        DailyWaterBalanceResponse body = mapper.toResponse(service.calculate(areaId, mapper.toInput(request)));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/areas/{areaId}/water-balance/{date}")
                .buildAndExpand(areaId, body.date()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PostMapping("/reprocess")
    @Operation(summary = "Reprocessa o balanço a partir de uma data",
            description = "Recalcula a cadeia de dias a partir da data informada e grava novas revisões, "
                    + "sem apagar as anteriores. Responde com a lista das novas revisões.")
    @ApiResponse(responseCode = "200", description = "Novas revisões gravadas")
    @BadRequestResponse
    @NotFoundResponse
    @ConflictResponse
    @UnprocessableEntityResponse
    public List<DailyWaterBalanceResponse> reprocess(
            @PathVariable UUID areaId, @Valid @RequestBody ReprocessWaterBalanceRequest request) {
        return service.reprocess(areaId, mapper.toInput(request)).stream().map(mapper::toResponse).toList();
    }

    @GetMapping
    @Operation(summary = "Lista os balanços correntes de forma paginada",
            description = "Filtros opcionais `from` e `to` (yyyy-MM-dd). Padrão: página 0, tamanho 20 (máximo 100).")
    @ApiResponse(responseCode = "200", description = "Página de balanços (vazia se nada corresponder)")
    @BadRequestResponse
    @NotFoundResponse
    public PagedResponse<DailyWaterBalanceResponse> list(
            @PathVariable UUID areaId,
            @Parameter(description = "Data inicial (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Número da página, iniciando em 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página, de 1 a 100") @RequestParam(required = false) Integer size) {
        return PagedResponse.from(queryService.findByPeriod(areaId, from, to, page, size), mapper::toResponse);
    }

    @GetMapping("/{date}")
    @Operation(summary = "Obtém o balanço corrente de uma data")
    @ApiResponse(responseCode = "200", description = "Balanço encontrado")
    @BadRequestResponse
    @NotFoundResponse
    public DailyWaterBalanceResponse get(
            @PathVariable UUID areaId,
            @Parameter(description = "Data (yyyy-MM-dd)")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return mapper.toResponse(queryService.findByDate(areaId, date));
    }

    @GetMapping("/{date}/revisions")
    @Operation(summary = "Lista todas as revisões do balanço de uma data",
            description = "Da revisão mais recente para a mais antiga.")
    @ApiResponse(responseCode = "200", description = "Revisões encontradas")
    @BadRequestResponse
    @NotFoundResponse
    public List<DailyWaterBalanceResponse> revisions(
            @PathVariable UUID areaId,
            @Parameter(description = "Data (yyyy-MM-dd)")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.findRevisions(areaId, date).stream().map(mapper::toResponse).toList();
    }
}
