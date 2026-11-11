package com.irrigation.support;

import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Especificação de faixa válida de um value object numérico, extraída dos requisitos.
 * Compartilhada entre os testes de exemplo e os testes de propriedade.
 *
 * @param name         nome do value object
 * @param factory      construtor do value object
 * @param min          limite inferior
 * @param minInclusive se o limite inferior é aceito
 * @param max          limite superior (inclusivo); {@code null} se não houver
 */
public record RangeSpec(
        String name,
        Function<BigDecimal, Object> factory,
        BigDecimal min,
        boolean minInclusive,
        BigDecimal max) {

    private static final BigDecimal STEP = new BigDecimal("0.01");

    public boolean accepts(BigDecimal value) {
        int cmp = value.compareTo(min);
        if (cmp < 0 || (cmp == 0 && !minInclusive)) {
            return false;
        }
        return max == null || value.compareTo(max) <= 0;
    }

    /** Menor valor aceito (no passo de 0,01). */
    public BigDecimal lowestValid() {
        return minInclusive ? min : min.add(STEP);
    }

    /** Maior valor rejeitado abaixo da faixa. */
    public BigDecimal highestBelow() {
        return minInclusive ? min.subtract(STEP) : min;
    }

    /** Menor valor rejeitado acima da faixa; {@code null} se não há limite superior. */
    public BigDecimal lowestAbove() {
        return max == null ? null : max.add(STEP);
    }

    @Override
    public String toString() {
        return name;
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    /** Value objects numéricos com faixa definida nos requisitos 1.14, 1.15, 2.13, 3.11–3.13, 4.8. */
    public static final List<RangeSpec> ALL = List.of(
            new RangeSpec("AreaInHectares", AreaInHectares::new, d("0"), false, d("1000000.00")),
            new RangeSpec("FlowRate", FlowRate::new, d("0"), false, d("1000000.00")),
            new RangeSpec("Kc", Kc::new, d("0"), false, d("2.0")),
            new RangeSpec("BulkDensity", BulkDensity::new, d("0"), false, d("3.00")),
            new RangeSpec("RootDepth", RootDepth::new, d("0"), false, d("5000.00")),
            new RangeSpec("VolumetricWaterContent", VolumetricWaterContent::new, d("0"), true, d("1")),
            new RangeSpec("IrrigationEfficiency", IrrigationEfficiency::new, d("0.01"), true, d("1")),
            new RangeSpec("AvailableWater", AvailableWater::new, d("0"), true, null));
}
