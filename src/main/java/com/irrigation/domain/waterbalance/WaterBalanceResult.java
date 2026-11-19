package com.irrigation.domain.waterbalance;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado do balanço hídrico de um dia, sem arredondamento de ETc, CAD, AFDi, AFDf e I.
 *
 * @param etc                   ETc, evapotranspiração da cultura (mm)
 * @param cad                   CAD, capacidade de água disponível (mm)
 * @param afdInitial            AFDi, água facilmente disponível no início do dia (mm)
 * @param afdFinal              AFDf, água facilmente disponível no fim do dia (mm), sem limite
 * @param idealIrrigation       I = AFDi − AFDf, com sinal (mm)
 * @param recommended           indica se há recomendação de irrigação
 * @param noRecommendationReason motivo da ausência de recomendação; nulo quando não se aplica
 * @param operatingTimeHours    tempo de funcionamento em horas (escala 10); zero se não recomendado
 *
 * (Req 7.9, 7.10, 15.1)
 */
public record WaterBalanceResult(
        BigDecimal etc,
        BigDecimal cad,
        BigDecimal afdInitial,
        BigDecimal afdFinal,
        BigDecimal idealIrrigation,
        boolean recommended,
        NoRecommendationReason noRecommendationReason,
        BigDecimal operatingTimeHours) {

    public WaterBalanceResult {
        Objects.requireNonNull(etc, "ETc não pode ser nula");
        Objects.requireNonNull(cad, "CAD não pode ser nula");
        Objects.requireNonNull(afdInitial, "AFDi não pode ser nula");
        Objects.requireNonNull(afdFinal, "AFDf não pode ser nula");
        Objects.requireNonNull(idealIrrigation, "I não pode ser nula");
        Objects.requireNonNull(operatingTimeHours, "Tempo de funcionamento não pode ser nulo");
    }
}
