package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Value object contendo os parâmetros de entrada para um cálculo de irrigação.
 * (Req 4.8 — cada parâmetro com seu intervalo validado)
 *
 * <p>Os parâmetros são validados no construtor. Se qualquer parâmetro estiver
 * ausente ou fora do intervalo, uma lista de parâmetros inválidos é coletada
 * e pode ser obtida via {@link #getValidationErrors()}.
 */
public class CalculationInput {

    private final BigDecimal eto;
    private final Kc kc;
    private final BigDecimal etc;
    private final BigDecimal effectivePrecipitation;
    private final BigDecimal currentSoilMoisture;
    private final BigDecimal waterStorageCapacity;
    private final IrrigationEfficiency irrigationEfficiency;
    private final BigDecimal netIrrigationDepth;
    private final BigDecimal grossIrrigationDepth;
    private final BigDecimal irrigatedArea;
    private final BigDecimal systemFlowRate;
    private final BigDecimal irrigationTime;

    public CalculationInput(
            BigDecimal eto,
            Kc kc,
            BigDecimal etc,
            BigDecimal effectivePrecipitation,
            BigDecimal currentSoilMoisture,
            BigDecimal waterStorageCapacity,
            IrrigationEfficiency irrigationEfficiency,
            BigDecimal netIrrigationDepth,
            BigDecimal grossIrrigationDepth,
            BigDecimal irrigatedArea,
            BigDecimal systemFlowRate,
            BigDecimal irrigationTime) {

        this.eto = eto;
        this.kc = kc;
        this.etc = etc;
        this.effectivePrecipitation = effectivePrecipitation;
        this.currentSoilMoisture = currentSoilMoisture;
        this.waterStorageCapacity = waterStorageCapacity;
        this.irrigationEfficiency = irrigationEfficiency;
        this.netIrrigationDepth = netIrrigationDepth;
        this.grossIrrigationDepth = grossIrrigationDepth;
        this.irrigatedArea = irrigatedArea;
        this.systemFlowRate = systemFlowRate;
        this.irrigationTime = irrigationTime;
    }

    /**
     * Valida todos os parâmetros e retorna a lista de nomes de parâmetros inválidos.
     * Retorna lista vazia se todos os parâmetros forem válidos.
     * (Req 4.11)
     */
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();

        validateRange(errors, "ETo", eto, "0", "15");
        // Kc valida a si mesmo no construtor; null check aqui
        if (kc == null) errors.add("Kc");
        validateRange(errors, "ETc", etc, "0", "15");
        validateRange(errors, "precipitação efetiva", effectivePrecipitation, "0", "500");
        validateRange(errors, "umidade atual do solo", currentSoilMoisture, "0", "100");
        validateRange(errors, "capacidade de armazenamento de água", waterStorageCapacity, "0", "500");
        if (irrigationEfficiency == null) errors.add("eficiência do sistema de irrigação");
        validateRange(errors, "lâmina líquida de irrigação", netIrrigationDepth, "0", "500");
        validateRange(errors, "lâmina bruta de irrigação", grossIrrigationDepth, "0", "500");
        validateRange(errors, "área irrigada", irrigatedArea, "0.01", "99999.99");
        validateRange(errors, "vazão do sistema", systemFlowRate, "0.01", "9999.99");
        validateRange(errors, "tempo de irrigação", irrigationTime, "0", "999.99");

        return errors;
    }

    /**
     * Retorna true se todos os parâmetros são válidos.
     */
    public boolean isValid() {
        return getValidationErrors().isEmpty();
    }

    private void validateRange(List<String> errors, String paramName, BigDecimal value, String min, String max) {
        if (value == null) {
            errors.add(paramName);
            return;
        }
        BigDecimal minVal = new BigDecimal(min);
        BigDecimal maxVal = new BigDecimal(max);
        if (value.compareTo(minVal) < 0 || value.compareTo(maxVal) > 0) {
            errors.add(paramName);
        }
    }

    // Getters

    public BigDecimal getEto() { return eto; }
    public Kc getKc() { return kc; }
    public BigDecimal getEtc() { return etc; }
    public BigDecimal getEffectivePrecipitation() { return effectivePrecipitation; }
    public BigDecimal getCurrentSoilMoisture() { return currentSoilMoisture; }
    public BigDecimal getWaterStorageCapacity() { return waterStorageCapacity; }
    public IrrigationEfficiency getIrrigationEfficiency() { return irrigationEfficiency; }
    public BigDecimal getNetIrrigationDepth() { return netIrrigationDepth; }
    public BigDecimal getGrossIrrigationDepth() { return grossIrrigationDepth; }
    public BigDecimal getIrrigatedArea() { return irrigatedArea; }
    public BigDecimal getSystemFlowRate() { return systemFlowRate; }
    public BigDecimal getIrrigationTime() { return irrigationTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalculationInput that = (CalculationInput) o;
        return Objects.equals(eto, that.eto) &&
                Objects.equals(kc, that.kc) &&
                Objects.equals(etc, that.etc) &&
                Objects.equals(effectivePrecipitation, that.effectivePrecipitation) &&
                Objects.equals(currentSoilMoisture, that.currentSoilMoisture) &&
                Objects.equals(waterStorageCapacity, that.waterStorageCapacity) &&
                Objects.equals(irrigationEfficiency, that.irrigationEfficiency) &&
                Objects.equals(netIrrigationDepth, that.netIrrigationDepth) &&
                Objects.equals(grossIrrigationDepth, that.grossIrrigationDepth) &&
                Objects.equals(irrigatedArea, that.irrigatedArea) &&
                Objects.equals(systemFlowRate, that.systemFlowRate) &&
                Objects.equals(irrigationTime, that.irrigationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eto, kc, etc, effectivePrecipitation, currentSoilMoisture,
                waterStorageCapacity, irrigationEfficiency, netIrrigationDepth,
                grossIrrigationDepth, irrigatedArea, systemFlowRate, irrigationTime);
    }
}
