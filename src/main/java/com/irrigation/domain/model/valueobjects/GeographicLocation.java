package com.irrigation.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representando localização geográfica em graus decimais (WGS84).
 * (Req 1.16, 1.17 — latitude -90..90, longitude -180..180; Item em Aberto 4)
 */
public record GeographicLocation(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal LAT_MIN = new BigDecimal("-90");
    private static final BigDecimal LAT_MAX = new BigDecimal("90");
    private static final BigDecimal LON_MIN = new BigDecimal("-180");
    private static final BigDecimal LON_MAX = new BigDecimal("180");

    public GeographicLocation {
        Objects.requireNonNull(latitude, "latitude não pode ser nula");
        Objects.requireNonNull(longitude, "longitude não pode ser nula");
        if (latitude.compareTo(LAT_MIN) < 0 || latitude.compareTo(LAT_MAX) > 0) {
            throw new IllegalArgumentException("latitude deve estar entre -90 e 90 graus");
        }
        if (longitude.compareTo(LON_MIN) < 0 || longitude.compareTo(LON_MAX) > 0) {
            throw new IllegalArgumentException("longitude deve estar entre -180 e 180 graus");
        }
    }
}
