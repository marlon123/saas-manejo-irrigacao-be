package com.irrigation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação de um balanço hídrico diário, em três grupos: {@code inputs}, {@code results} e
 * {@code control}. Os valores já chegam arredondados para apresentação (Req 15.4 a 15.7).
 * {@code idealIrrigationMm} (I, resultado) e {@code appliedIrrigationMm} (IR, entrada) são distintos.
 */
@Schema(description = "Balanço hídrico diário de uma área")
public record DailyWaterBalanceResponse(
        @Schema(description = "Identificador do registro") UUID id,
        @Schema(description = "Identificador da área") UUID areaId,
        @Schema(description = "Data do balanço", example = "2026-04-10") LocalDate date,
        @Schema(description = "Dia do ciclo (TR)", example = "42") int cycleDay,
        @Schema(description = "Plano de manejo usado") UUID managementPlanId,
        @Schema(description = "Cultura usada") UUID cropId,
        @Schema(description = "Perfil de solo usado") UUID soilProfileId,
        @Schema(description = "Entradas do cálculo (snapshot)") InputsResponse inputs,
        @Schema(description = "Resultados do cálculo") ResultsResponse results,
        @Schema(description = "Dados de controle da revisão") ControlResponse control) {

    @Schema(description = "Entradas do cálculo; valores em mm com 2 casas")
    public record InputsResponse(
            @Schema(description = "ETP em mm") BigDecimal etpMm,
            @Schema(description = "Precipitação P em mm") BigDecimal precipitationMm,
            @Schema(description = "Irrigação real IR em mm") BigDecimal appliedIrrigationMm,
            @Schema(description = "Capacidade de campo em %") BigDecimal fieldCapacityPct,
            @Schema(description = "Ponto de murcha permanente em %") BigDecimal wiltingPointPct,
            @Schema(description = "Densidade do solo em g/cm³") BigDecimal bulkDensityGCm3,
            @Schema(description = "Profundidade radicular Z em mm") BigDecimal rootDepthMm,
            @Schema(description = "Coeficiente de cultura Kc (adimensional)") BigDecimal kc,
            @Schema(description = "Fator de disponibilidade F (adimensional)") BigDecimal fFactor,
            @Schema(description = "Taxa do aspersor em mm/h") BigDecimal sprinklerRateMmPerHour) {
    }

    @Schema(description = "Tempo de funcionamento")
    public record OperatingTimeResponse(
            @Schema(description = "Tempo em horas, com 4 casas decimais", example = "2.5000") BigDecimal hours,
            @Schema(description = "Parte em horas inteiras", example = "2") int wholeHours,
            @Schema(description = "Minutos restantes", example = "30") int minutes) {
    }

    @Schema(description = "Resultados do cálculo; valores em mm com 2 casas (HALF_UP)")
    public record ResultsResponse(
            @Schema(description = "Evapotranspiração da cultura ETc em mm") BigDecimal etcMm,
            @Schema(description = "Capacidade de água disponível CAD em mm") BigDecimal cadMm,
            @Schema(description = "Água facilmente disponível inicial em mm") BigDecimal afdInitialMm,
            @Schema(description = "Água facilmente disponível final em mm") BigDecimal afdFinalMm,
            @Schema(description = "Irrigação ideal I (resultado) em mm") BigDecimal idealIrrigationMm,
            @Schema(description = "Indica se há recomendação de irrigar") boolean irrigationRecommended,
            @Schema(description = "Motivo de não haver recomendação; nulo quando há", nullable = true)
            String noRecommendationReason,
            @Schema(description = "Tempo de funcionamento do sistema") OperatingTimeResponse operatingTime) {
    }

    @Schema(description = "Dados de controle: metodologia, instante do cálculo, revisão e indicador de corrente")
    public record ControlResponse(
            @Schema(description = "Instante do cálculo (UTC)") Instant calculatedAt,
            @Schema(description = "Identificador da metodologia") String methodologyId,
            @Schema(description = "Versão da metodologia") String methodologyVersion,
            @Schema(description = "Número da revisão, a partir de 1") int revision,
            @Schema(description = "Indica se esta é a revisão corrente") boolean current) {
    }
}