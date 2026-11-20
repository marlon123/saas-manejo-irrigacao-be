package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Balanço hídrico diário de uma área. Registro imutável: a única mudança permitida é
 * {@link #markNotCurrent()}, que devolve uma cópia com {@code current = false}.
 *
 * <p>{@code inputs} é o snapshot de todas as entradas usadas no cálculo (ETP, P, IR, CC, PMP, D, Z, Kc, F,
 * taxa do aspersor, indicador de primeiro dia e AFDf anterior), e {@code result} guarda os resultados sem
 * arredondamento. Cada recálculo gera uma nova revisão; a revisão anterior deixa de ser a corrente.
 *
 * @param id                 identificador do registro
 * @param areaId             área
 * @param date               data do balanço
 * @param cropId             cultura usada no cálculo
 * @param soilProfileId      perfil de solo usado no cálculo
 * @param managementPlanId   plano de manejo usado no cálculo
 * @param cycleDay           dia do ciclo (TR)
 * @param inputs             snapshot das entradas
 * @param result             resultados do cálculo
 * @param calculatedAt       instante do cálculo (UTC)
 * @param methodologyId      identificador da metodologia aplicada
 * @param methodologyVersion versão da metodologia aplicada
 * @param revision           número da revisão, maior ou igual a 1
 * @param current            indica se é a revisão corrente da data
 *
 * (Req 12.1, 12.2, 12.3, 12.4, 12.7, 13.2)
 */
public record DailyWaterBalance(
        UUID id,
        UUID areaId,
        LocalDate date,
        UUID cropId,
        UUID soilProfileId,
        UUID managementPlanId,
        CycleDay cycleDay,
        WaterBalanceInputs inputs,
        WaterBalanceResult result,
        Instant calculatedAt,
        String methodologyId,
        String methodologyVersion,
        int revision,
        boolean current) {

    public DailyWaterBalance {
        Objects.requireNonNull(id, "id não pode ser nulo");
        Objects.requireNonNull(areaId, "areaId não pode ser nulo");
        Objects.requireNonNull(date, "date não pode ser nula");
        Objects.requireNonNull(cropId, "cropId não pode ser nulo");
        Objects.requireNonNull(soilProfileId, "soilProfileId não pode ser nulo");
        Objects.requireNonNull(managementPlanId, "managementPlanId não pode ser nulo");
        Objects.requireNonNull(cycleDay, "cycleDay não pode ser nulo");
        Objects.requireNonNull(inputs, "inputs não pode ser nulo");
        Objects.requireNonNull(result, "result não pode ser nulo");
        Objects.requireNonNull(calculatedAt, "calculatedAt não pode ser nulo");
        Objects.requireNonNull(methodologyId, "methodologyId não pode ser nulo");
        Objects.requireNonNull(methodologyVersion, "methodologyVersion não pode ser nulo");
        if (revision < 1) {
            throw new IllegalArgumentException("revision deve ser maior ou igual a 1");
        }
    }

    /** Devolve uma cópia deste balanço marcada como não corrente; o original não é alterado. */
    public DailyWaterBalance markNotCurrent() {
        return new DailyWaterBalance(id, areaId, date, cropId, soilProfileId, managementPlanId, cycleDay,
                inputs, result, calculatedAt, methodologyId, methodologyVersion, revision, false);
    }
}
