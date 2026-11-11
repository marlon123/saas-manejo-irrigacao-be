package com.irrigation.domain.calculation;

import com.irrigation.domain.calculation.exception.DuplicateMethodologyException;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry de metodologias de cálculo de irrigação. (Req 4.1, 4.3–4.11)
 *
 * <p>Serviço de domínio puro, sem dependências de Spring ou JPA.
 * As implementações de {@link CalculationMethod} são registradas e
 * identificadas por um {@link MethodologyIdentifier} único.
 *
 * <p>Comportamento:
 * <ul>
 *   <li>{@code calculate} valida {@link CalculationInput} primeiro (Req 4.11).</li>
 *   <li>Se a metodologia não estiver registrada, lança
 *       {@link MethodologyNotAvailableException} (Req 4.6).</li>
 *   <li>Caso contrário, delega para {@link CalculationMethod#calculate} (Req 4.5).</li>
 *   <li>{@code register} lança {@link DuplicateMethodologyException} se o id
 *       já existir, preservando o registro existente (Req 4.10).</li>
 * </ul>
 */
public class CalculationEngine {

    private final Map<MethodologyIdentifier, CalculationMethod> methods = new HashMap<>();

    /**
     * Registra uma metodologia de cálculo.
     * (Req 4.3, 4.9, 4.10)
     *
     * @param method a metodologia a registrar
     * @throws DuplicateMethodologyException se o identificador já estiver registrado
     */
    public void register(CalculationMethod method) {
        Objects.requireNonNull(method, "método de cálculo não pode ser nulo");
        MethodologyIdentifier id = method.methodologyId();
        if (methods.containsKey(id)) {
            throw new DuplicateMethodologyException(id);
        }
        methods.put(id, method);
    }

    /**
     * Executa um cálculo com a metodologia e entradas fornecidas.
     * (Req 4.5, 4.6, 4.11)
     *
     * @param id    identificador da metodologia
     * @param input parâmetros de entrada
     * @return resultado do cálculo
     * @throws InvalidCalculationInputException se entradas forem inválidas
     * @throws MethodologyNotAvailableException se a metodologia não estiver registrada
     */
    public CalculationResult calculate(MethodologyIdentifier id, CalculationInput input) {
        Objects.requireNonNull(id, "identificador de metodologia não pode ser nulo");
        Objects.requireNonNull(input, "entrada de cálculo não pode ser nula");

        // Req 4.11 — valida entradas antes de executar
        var validationErrors = input.getValidationErrors();
        if (!validationErrors.isEmpty()) {
            throw new InvalidCalculationInputException(validationErrors);
        }

        // Req 4.6 — verifica se a metodologia está registrada
        CalculationMethod method = methods.get(id);
        if (method == null) {
            throw new MethodologyNotAvailableException(id);
        }

        // Req 4.5 — delega para o método registrado
        return method.calculate(input);
    }

    /**
     * Verifica se uma metodologia está registrada.
     */
    public boolean isRegistered(MethodologyIdentifier id) {
        return methods.containsKey(id);
    }

    /**
     * Retorna as metodologias registradas (vista imutável).
     */
    public Map<MethodologyIdentifier, CalculationMethod> getRegisteredMethods() {
        return Collections.unmodifiableMap(methods);
    }
}
