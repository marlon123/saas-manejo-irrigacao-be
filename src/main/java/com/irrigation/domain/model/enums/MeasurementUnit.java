package com.irrigation.domain.model.enums;

/**
 * Unidades de medida explícitas para parâmetros de solo e cálculo.
 * (Req 3.14 — cada parâmetro deve carregar uma unidade explícita)
 */
public enum MeasurementUnit {
    HECTARES("ha"),
    SQUARE_METERS("m²"),
    CUBIC_METERS_PER_HOUR("m³/h"),
    LITERS_PER_SECOND("L/s"),
    MILLIMETERS("mm"),
    MILLIMETERS_PER_DAY("mm/dia"),
    GRAMS_PER_CUBIC_CENTIMETER("g/cm³"),
    PERCENT("%"),
    VOLUMETRIC_WATER_CONTENT("cm³/cm³"),
    HOURS("h"),
    FRACTION("fração"),
    DIMENSIONLESS("adimensional");

    private final String symbol;

    MeasurementUnit(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
