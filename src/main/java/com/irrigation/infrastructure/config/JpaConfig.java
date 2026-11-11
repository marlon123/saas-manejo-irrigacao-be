package com.irrigation.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuração da persistência: restringe o escaneamento de entidades JPA e de
 * repositórios Spring Data à camada de infraestrutura.
 */
@Configuration
@EntityScan(basePackages = "com.irrigation.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.irrigation.infrastructure.persistence.repository")
public class JpaConfig {
}
