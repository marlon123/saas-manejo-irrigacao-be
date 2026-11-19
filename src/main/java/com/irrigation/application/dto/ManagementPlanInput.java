package com.irrigation.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados de entrada para criar ou atualizar um plano de manejo (Req 4.1 a 4.5 do módulo de balanço hídrico).
 *
 * <p>Os tipos são brutos e anuláveis para que o serviço acumule todas as violações em uma só resposta 400
 * (Req 4.5). {@code fFactor} é o fator de disponibilidade F (adimensional, entre 0 exclusive e 1, nunca
 * dividido por 100). {@code sprinklerRateMmPerHour} é a taxa do aspersor, em mm/h. {@code endDate} é opcional.
 *
 * <p>Um mesmo input serve para criar e atualizar. Na atualização, só {@code fFactor},
 * {@code sprinklerRateMmPerHour} e {@code endDate} mudam; trocar cultura ou solo com balanço existente é
 * recusado pelo serviço (Req 4.10).
 */
public record ManagementPlanInput(
        UUID cropId,
        UUID soilProfileId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal fFactor,
        BigDecimal sprinklerRateMmPerHour) {
}
