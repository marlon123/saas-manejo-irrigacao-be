package com.irrigation.application.dto;

import com.irrigation.application.waterbalance.PresentationRounding;
import com.irrigation.application.waterbalance.PresentationRounding.OperatingTimeView;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Saída de um balanço hídrico diário, em três grupos: {@code inputs}, {@code results} e {@code control}.
 *
 * <p>O arredondamento de apresentação (Requisitos 15.4 a 15.7) é aplicado só na montagem deste DTO, por
 * {@link PresentationRounding}. O valor persistido nunca é alterado. {@code idealIrrigationMm} (I, resultado) e
 * {@code appliedIrrigationMm} (IR, entrada) são campos distintos (Requisito 9.5).
 *
 * @param id               identificador do registro
 * @param areaId           área
 * @param date             data do balanço
 * @param cycleDay         dia do ciclo (TR)
 * @param managementPlanId plano de manejo usado
 * @param cropId           cultura usada
 * @param soilProfileId    perfil de solo usado
 * @param inputs           snapshot das entradas
 * @param results          resultados do cálculo
 * @param control          dados de controle da revisão
 */
public record WaterBalanceOutput(
        UUID id,
        UUID areaId,
        LocalDate date,
        int cycleDay,
        UUID managementPlanId,
        UUID cropId,
        UUID soilProfileId,
        WaterBalanceInputsOutput inputs,
        WaterBalanceResultsOutput results,
        WaterBalanceControlOutput control) {

    /** Entradas do cálculo. Valores em mm com 2 casas; os demais como armazenados. */
    public record WaterBalanceInputsOutput(
            BigDecimal etpMm,
            BigDecimal precipitationMm,
            BigDecimal appliedIrrigationMm,
            BigDecimal fieldCapacityPct,
            BigDecimal wiltingPointPct,
            BigDecimal bulkDensityGCm3,
            BigDecimal rootDepthMm,
            BigDecimal kc,
            BigDecimal fFactor,
            BigDecimal sprinklerRateMmPerHour) {

        static WaterBalanceInputsOutput from(WaterBalanceInputs in) {
            return new WaterBalanceInputsOutput(
                    PresentationRounding.roundMm(in.etp().value()),
                    PresentationRounding.roundMm(in.precipitation().value()),
                    PresentationRounding.roundMm(in.appliedIrrigation().value()),
                    in.fieldCapacity().value(),
                    in.permanentWiltingPoint().value(),
                    in.bulkDensity().value(),
                    PresentationRounding.roundMm(in.rootDepth().value()),
                    in.kc().value(),
                    in.availabilityFactor().value(),
                    in.sprinklerRate().value());
        }
    }

    /** Tempo de funcionamento: horas com 4 casas e a distribuição em horas inteiras e minutos. */
    public record OperatingTimeOutput(BigDecimal hours, int wholeHours, int minutes) {

        static OperatingTimeOutput from(BigDecimal persistedHours) {
            OperatingTimeView view = PresentationRounding.operatingTime(persistedHours);
            return new OperatingTimeOutput(view.hours(), view.wholeHours(), view.minutes());
        }
    }

    /** Resultados do cálculo. Valores em mm com 2 casas, {@code HALF_UP}. */
    public record WaterBalanceResultsOutput(
            BigDecimal etcMm,
            BigDecimal cadMm,
            BigDecimal afdInitialMm,
            BigDecimal afdFinalMm,
            BigDecimal idealIrrigationMm,
            boolean irrigationRecommended,
            String noRecommendationReason,
            OperatingTimeOutput operatingTime) {

        static WaterBalanceResultsOutput from(WaterBalanceResult r) {
            return new WaterBalanceResultsOutput(
                    PresentationRounding.roundMm(r.etc()),
                    PresentationRounding.roundMm(r.cad()),
                    PresentationRounding.roundMm(r.afdInitial()),
                    PresentationRounding.roundMm(r.afdFinal()),
                    PresentationRounding.roundMm(r.idealIrrigation()),
                    r.recommended(),
                    r.noRecommendationReason() == null ? null : r.noRecommendationReason().name(),
                    OperatingTimeOutput.from(r.operatingTimeHours()));
        }
    }

    /** Dados de controle: metodologia, instante do cálculo, revisão e indicador de corrente. */
    public record WaterBalanceControlOutput(
            Instant calculatedAt,
            String methodologyId,
            String methodologyVersion,
            int revision,
            boolean current) {
    }

    /** Monta a saída a partir do agregado, arredondando só a apresentação. */
    public static WaterBalanceOutput from(DailyWaterBalance balance) {
        return new WaterBalanceOutput(
                balance.id(),
                balance.areaId(),
                balance.date(),
                balance.cycleDay().value(),
                balance.managementPlanId(),
                balance.cropId(),
                balance.soilProfileId(),
                WaterBalanceInputsOutput.from(balance.inputs()),
                WaterBalanceResultsOutput.from(balance.result()),
                new WaterBalanceControlOutput(
                        balance.calculatedAt(),
                        balance.methodologyId(),
                        balance.methodologyVersion(),
                        balance.revision(),
                        balance.current()));
    }
}
