package com.irrigation.domain.waterbalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculador puro do balanço hídrico de um dia. Sem estado, sem relógio, sem banco e sem framework.
 *
 * <p>Fórmulas (requirements.md, seção 4), todas em {@link BigDecimal} exato:
 * <ul>
 *   <li>{@code ETc = KC × ETP}</li>
 *   <li>{@code CAD = 0,01 × (CC − PMP) × D × Z}</li>
 *   <li>{@code AFDi = CAD × F} no primeiro dia do plano; nos demais, a AFDf do dia anterior</li>
 *   <li>{@code AFDf = AFDi + (P + IR − ETc)}, sem limitar nem truncar</li>
 *   <li>{@code I = AFDi − AFDf}, com sinal</li>
 *   <li>{@code recommended = (I > 0) E (não é o primeiro dia)}</li>
 *   <li>{@code Operating_Time = I ÷ Sprinkler_Rate} (escala 10, {@link RoundingMode#HALF_EVEN})
 *       quando recomendado; zero caso contrário</li>
 * </ul>
 * ETc, CAD, AFDi, AFDf e I não são arredondados. A conversão de apresentação não fica aqui.
 *
 * (Req 7.1, 8.1 a 8.3, 8.5, 8.6, 9.1 a 9.6, 10.1, 10.2, 15.1 a 15.3, 15.9, 15.10)
 */
public final class WaterBalanceCalculator {

    /** Identificador da metodologia do balanço hídrico. */
    public static final String METHODOLOGY_ID = "WATER_BALANCE_AFD";

    /** Versão da metodologia do balanço hídrico. */
    public static final String METHODOLOGY_VERSION = "1.0";

    /** Escala do tempo de funcionamento em horas. */
    static final int OPERATING_TIME_SCALE = 10;

    private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");

    /**
     * Calcula o balanço hídrico de um dia.
     *
     * @throws IllegalArgumentException se o divisor da taxa de aplicação for zero
     */
    public WaterBalanceResult calculate(WaterBalanceInputs in) {
        Objects.requireNonNull(in, "As entradas não podem ser nulas");

        BigDecimal etc = in.kc().value().multiply(in.etp().value());

        BigDecimal cad = ONE_HUNDREDTH
                .multiply(in.fieldCapacity().value().subtract(in.permanentWiltingPoint().value()))
                .multiply(in.bulkDensity().value())
                .multiply(in.rootDepth().value());

        BigDecimal afdInitial = in.firstDayOfPlan()
                ? cad.multiply(in.availabilityFactor().value())
                : in.previousAfdFinal();

        BigDecimal afdFinal = afdInitial.add(
                in.precipitation().value().add(in.appliedIrrigation().value()).subtract(etc));

        BigDecimal idealIrrigation = afdInitial.subtract(afdFinal);

        boolean recommended = !in.firstDayOfPlan() && idealIrrigation.signum() > 0;

        BigDecimal operatingTime = recommended
                ? divide(idealIrrigation, in.sprinklerRate().value())
                : BigDecimal.ZERO.setScale(OPERATING_TIME_SCALE);

        NoRecommendationReason reason =
                in.firstDayOfPlan() ? NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE : null;

        return new WaterBalanceResult(
                etc, cad, afdInitial, afdFinal, idealIrrigation, recommended, reason, operatingTime);
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new IllegalArgumentException("divisor inválido");
        }
        return dividend.divide(divisor, OPERATING_TIME_SCALE, RoundingMode.HALF_EVEN);
    }
}
