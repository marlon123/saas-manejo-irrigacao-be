package com.irrigation.application.waterbalance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Política única de apresentação do balanço hídrico (Requisitos 15.4 a 15.7).
 *
 * <p>Classe pura: sem Spring e só com {@link BigDecimal}. Nunca é usada em cálculo
 * (Requisito 15.8); serve apenas para formatar valores nos {@code Output}.
 */
public final class PresentationRounding {

    /** Casas decimais de valores em mm. */
    public static final int MM_SCALE = 2;
    /** Casas decimais de horas de funcionamento. */
    public static final int HOURS_SCALE = 4;

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    private static final BigInteger MINUTES_PER_HOUR_INT = BigInteger.valueOf(60);

    private PresentationRounding() {
    }

    /**
     * Tempo de funcionamento apresentado.
     *
     * @param hours      horas com 4 casas, {@code HALF_UP}
     * @param wholeHours horas inteiras da distribuição em horas e minutos
     * @param minutes    minutos de 0 a 59
     */
    public record OperatingTimeView(BigDecimal hours, int wholeHours, int minutes) {
    }

    /** Arredonda um valor em mm para 2 casas, {@code HALF_UP}. */
    public static BigDecimal roundMm(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return value.setScale(MM_SCALE, RoundingMode.HALF_UP);
    }

    /** Arredonda horas para 4 casas, {@code HALF_UP}. */
    public static BigDecimal roundHours(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return value.setScale(HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Monta a visão do tempo de funcionamento a partir do valor persistido (escala 10).
     * Os minutos totais são {@code round(horas × 60, HALF_UP)} sobre o valor sem arredondar
     * antes; depois são divididos em horas inteiras e minutos de 0 a 59 (59,6 min vira 1 h 00 min).
     *
     * @throws IllegalArgumentException se as horas forem negativas
     */
    public static OperatingTimeView operatingTime(BigDecimal persistedHours) {
        Objects.requireNonNull(persistedHours, "persistedHours");
        if (persistedHours.signum() < 0) {
            throw new IllegalArgumentException("horas de funcionamento negativas");
        }
        BigInteger totalMinutes = persistedHours.multiply(MINUTES_PER_HOUR)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigIntegerExact();
        BigInteger[] parts = totalMinutes.divideAndRemainder(MINUTES_PER_HOUR_INT);
        return new OperatingTimeView(
                roundHours(persistedHours),
                parts[0].intValueExact(),
                parts[1].intValueExact());
    }
}
