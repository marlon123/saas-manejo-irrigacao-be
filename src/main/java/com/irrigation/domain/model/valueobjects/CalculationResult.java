package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object contendo os resultados de um cálculo de irrigação.
 * (Req 4.7 — lâmina 0-500mm, volume 0-999999.99m³, tempo 0-999.99h)
 */
public class CalculationResult {

    private final BigDecimal irrigationDepth;
    private final BigDecimal waterVolume;
    private final BigDecimal irrigationTime;

    public CalculationResult(BigDecimal irrigationDepth, BigDecimal waterVolume, BigDecimal irrigationTime) {
        Objects.requireNonNull(irrigationDepth, "lâmina de irrigação não pode ser nula");
        Objects.requireNonNull(waterVolume, "volume de água não pode ser nulo");
        Objects.requireNonNull(irrigationTime, "tempo de irrigação não pode ser nulo");

        if (irrigationDepth.compareTo(BigDecimal.ZERO) < 0 || irrigationDepth.compareTo(new BigDecimal("500")) > 0) {
            throw new IllegalArgumentException("lâmina de irrigação deve estar entre 0 e 500 mm");
        }
        if (waterVolume.compareTo(BigDecimal.ZERO) < 0 || waterVolume.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new IllegalArgumentException("volume de água deve estar entre 0 e 999999.99 m³");
        }
        if (irrigationTime.compareTo(BigDecimal.ZERO) < 0 || irrigationTime.compareTo(new BigDecimal("999.99")) > 0) {
            throw new IllegalArgumentException("tempo de irrigação deve estar entre 0 e 999,99 horas");
        }

        this.irrigationDepth = irrigationDepth;
        this.waterVolume = waterVolume;
        this.irrigationTime = irrigationTime;
    }

    public BigDecimal getIrrigationDepth() { return irrigationDepth; }
    public BigDecimal getWaterVolume() { return waterVolume; }
    public BigDecimal getIrrigationTime() { return irrigationTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalculationResult that = (CalculationResult) o;
        return Objects.equals(irrigationDepth, that.irrigationDepth) &&
                Objects.equals(waterVolume, that.waterVolume) &&
                Objects.equals(irrigationTime, that.irrigationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(irrigationDepth, waterVolume, irrigationTime);
    }
}
