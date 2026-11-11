package com.irrigation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relógio injetável. As colunas de data/hora são {@code TIMESTAMP} sem fuso, então
 * todo instante é gravado em UTC. Injetar o {@link Clock} permite fixar o tempo nos testes.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
