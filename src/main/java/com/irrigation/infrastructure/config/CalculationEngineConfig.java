package com.irrigation.infrastructure.config;

import com.irrigation.domain.calculation.CalculationEngine;
import com.irrigation.domain.calculation.CalculationMethod;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único ponto em que o motor de cálculo (Java puro) encontra o Spring.
 *
 * <p>Todo bean {@link CalculationMethod} existente no contexto é registrado
 * automaticamente no {@link CalculationEngine}. Nesta fase nenhuma metodologia
 * existe (fórmulas deferidas — Item em Aberto 1), então o motor inicia vazio.
 * Para adicionar uma metodologia basta declarar um novo bean; nada aqui muda
 * (Req 4.3, 4.9).
 */
@Configuration
public class CalculationEngineConfig {

    @Bean
    public CalculationEngine calculationEngine(ObjectProvider<CalculationMethod> methods) {
        CalculationEngine engine = new CalculationEngine();
        methods.orderedStream().forEach(engine::register);
        return engine;
    }
}
