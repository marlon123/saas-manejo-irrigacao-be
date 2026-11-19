package com.irrigation.domain.waterbalance;

import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Entradas do calculador do balanço hídrico de um dia.
 *
 * <p>Combinações válidas de {@code firstDayOfPlan} e {@code previousAfdFinal}:
 * no primeiro dia do plano, {@code previousAfdFinal} deve ser nulo; nos demais dias,
 * deve estar presente. Qualquer outra combinação lança {@link IllegalArgumentException}.
 *
 * @param etp                ETP do dia (mm)
 * @param precipitation      P, precipitação do dia (mm)
 * @param appliedIrrigation  IR, irrigação real aplicada no dia (mm)
 * @param fieldCapacity      CC gravimétrica (%)
 * @param permanentWiltingPoint PMP gravimétrico (%)
 * @param bulkDensity        D, densidade do solo
 * @param rootDepth          Z, profundidade radicular (mm)
 * @param kc                 KC, coeficiente de cultura
 * @param availabilityFactor F, fator de disponibilidade (0 a 1)
 * @param sprinklerRate      taxa de aplicação do aspersor (mm/h)
 * @param firstDayOfPlan     indica o primeiro dia do plano de manejo
 * @param previousAfdFinal   AFDf do dia anterior; nulo no primeiro dia do plano
 *
 * (Req 7.9, 7.10, 15.1)
 */
public record WaterBalanceInputs(
        EtpDepth etp,
        PrecipitationDepth precipitation,
        AppliedIrrigationDepth appliedIrrigation,
        GravimetricWaterContent fieldCapacity,
        GravimetricWaterContent permanentWiltingPoint,
        BulkDensity bulkDensity,
        RootDepth rootDepth,
        Kc kc,
        AvailabilityFactor availabilityFactor,
        SprinklerRate sprinklerRate,
        boolean firstDayOfPlan,
        BigDecimal previousAfdFinal) {

    public WaterBalanceInputs {
        Objects.requireNonNull(etp, "ETP não pode ser nula");
        Objects.requireNonNull(precipitation, "P não pode ser nula");
        Objects.requireNonNull(appliedIrrigation, "IR não pode ser nula");
        Objects.requireNonNull(fieldCapacity, "CC não pode ser nula");
        Objects.requireNonNull(permanentWiltingPoint, "PMP não pode ser nulo");
        Objects.requireNonNull(bulkDensity, "D não pode ser nula");
        Objects.requireNonNull(rootDepth, "Z não pode ser nula");
        Objects.requireNonNull(kc, "KC não pode ser nulo");
        Objects.requireNonNull(availabilityFactor, "F não pode ser nulo");
        Objects.requireNonNull(sprinklerRate, "Sprinkler_Rate não pode ser nula");
        if (firstDayOfPlan && previousAfdFinal != null) {
            throw new IllegalArgumentException(
                    "No primeiro dia do plano a AFDf anterior deve ser nula");
        }
        if (!firstDayOfPlan && previousAfdFinal == null) {
            throw new IllegalArgumentException(
                    "Nos dias seguintes ao primeiro a AFDf anterior é obrigatória");
        }
    }
}
