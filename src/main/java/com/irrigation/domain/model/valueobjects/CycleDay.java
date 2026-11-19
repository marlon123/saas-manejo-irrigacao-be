package com.irrigation.domain.model.valueobjects;

/**
 * Value object representando o dia do ciclo (TR), um inteiro apenas registrado.
 * (A-04 — maior ou igual a 1, sem limite superior e sem verificação de sequência)
 * O limite inferior fica na constante {@link #MIN}, único ponto a mudar se o usuário decidir
 * alterar a regra (DP-03). Para criar um limite superior, basta acrescentar uma constante e a verificação.
 */
public record CycleDay(int value) {

    public static final int MIN = 1;

    public CycleDay {
        if (value < MIN) {
            throw new IllegalArgumentException("dia do ciclo (TR) deve ser maior ou igual a " + MIN);
        }
    }
}
