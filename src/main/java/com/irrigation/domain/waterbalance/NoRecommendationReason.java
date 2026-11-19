package com.irrigation.domain.waterbalance;

/**
 * Motivo pelo qual um dia não gera recomendação de irrigação.
 * (Req 9.4)
 */
public enum NoRecommendationReason {

    /** Primeiro dia do primeiro ciclo do plano: não há recomendação, independentemente do sinal de I. */
    FIRST_DAY_OF_FIRST_CYCLE
}
