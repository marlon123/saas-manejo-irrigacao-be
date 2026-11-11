package com.irrigation.domain.calculation;

import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;

/**
 * Contrato que metodologias de cálculo de irrigação implementam.
 * (Req 4.2) — interface pura de domínio, sem dependências de framework.
 *
 * <p>Implementações concretas (fórmulas agronômicas) são deferidas
 * (Item em Aberto 1). Novas implementações são registradas no
 * {@link CalculationEngine} sem modificar as existentes (Req 4.9).
 */
public interface CalculationMethod {

    /**
     * Retorna o identificador único desta metodologia.
     */
    MethodologyIdentifier methodologyId();

    /**
     * Retorna a versão da metodologia.
     */
    String version();

    /**
     * Executa o cálculo de irrigação com os parâmetros fornecidos.
     *
     * @param input parâmetros de entrada validados
     * @return resultado do cálculo
     */
    CalculationResult calculate(CalculationInput input);
}
