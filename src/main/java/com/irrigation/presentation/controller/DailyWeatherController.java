package com.irrigation.presentation.controller;

import com.irrigation.application.service.DailyWeatherService;
import com.irrigation.presentation.docs.BadRequestResponse;
import com.irrigation.presentation.docs.NotFoundResponse;
import com.irrigation.presentation.dto.DailyWeatherRequest;
import com.irrigation.presentation.dto.DailyWeatherResponse;
import com.irrigation.presentation.mapper.DailyWeatherDtoMapper;
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
 * Endpoints de meteorologia diária (ETP e precipitação) de uma área (Req 5).
 */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/daily-weather")
@Tag(name = "Meteorologia diária", description = "ETP e precipitação diárias por área")
public class DailyWeatherController {

    private final DailyWeatherService service;
    private final DailyWeatherDtoMapper mapper;

    public DailyWeatherController(DailyWeatherService service, DailyWeatherDtoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Registra a meteorologia de um dia (upsert)",
            description = "A data vai no corpo. Se já existir registro para a área e a data, ele é substituído. "
                    + "Responde 200 com o registro persistido.")
    @ApiResponse(responseCode = "200", description = "Registro gravado")
    @BadRequestResponse
    @NotFoundResponse
    public DailyWeatherResponse upsert(@PathVariable UUID areaId, @Valid @RequestBody DailyWeatherRequest request) {
        return mapper.toResponse(service.upsert(areaId, mapper.toInput(request)));
    }

    @GetMapping
    @Operation(summary = "Lista a meteorologia de um período",
            description = "Período inclusivo, de até 366 dias, com datas no formato yyyy-MM-dd.")
    @ApiResponse(responseCode = "200", description = "Registros do período (lista vazia se não houver)")
    @BadRequestResponse
    @NotFoundResponse
    public List<DailyWeatherResponse> list(
            @PathVariable UUID areaId,
            @Parameter(description = "Data inicial (yyyy-MM-dd)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final (yyyy-MM-dd)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.findByPeriod(areaId, from, to).stream().map(mapper::toResponse).toList();
    }
}
