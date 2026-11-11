package com.irrigation.domain.model;

import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representando uma recomendação de irrigação (registro de cálculo).
 * (Req 5.1, 5.7) — imutável após criação.
 *
 * <p>Armazena um snapshot completo e imutável dos dados de entrada, resultados,
 * metodologia e versão. Quando a metodologia evolui, as recomendações anteriores
 * permanecem inalteradas (Req 5.7).
 */
public class Recommendation {

    private final UUID id;
    private final UUID areaId;
    private final LocalDateTime dateTime;
    private final CalculationInput inputData;
    private final CalculationResult results;
    private final MethodologyIdentifier methodologyId;
    private final String methodologyVersion;

    public Recommendation(
            UUID id,
            UUID areaId,
            LocalDateTime dateTime,
            CalculationInput inputData,
            CalculationResult results,
            MethodologyIdentifier methodologyId,
            String methodologyVersion) {

        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        this.areaId = Objects.requireNonNull(areaId, "areaId não pode ser nulo");
        this.dateTime = Objects.requireNonNull(dateTime, "data e hora não podem ser nulas");
        this.inputData = Objects.requireNonNull(inputData, "dados de entrada não podem ser nulos");
        this.results = Objects.requireNonNull(results, "resultados não podem ser nulos");
        this.methodologyId = Objects.requireNonNull(methodologyId, "identificador de metodologia não pode ser nulo");
        this.methodologyVersion = Objects.requireNonNull(methodologyVersion, "versão de metodologia não pode ser nula");
    }

    public UUID getId() { return id; }
    public UUID getAreaId() { return areaId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public CalculationInput getInputData() { return inputData; }
    public CalculationResult getResults() { return results; }
    public MethodologyIdentifier getMethodologyId() { return methodologyId; }
    public String getMethodologyVersion() { return methodologyVersion; }
}
