package com.irrigation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação irrigation-management-backend.
 *
 * <p>Backend SaaS para manejo de irrigação agrícola. Arquitetura modular monolítica
 * baseada em Clean Architecture / DDD Pragmático com quatro camadas:
 * Domain, Application, Infrastructure e Presentation.
 */
@SpringBootApplication
public class IrrigationApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrrigationApplication.class, args);
    }
}
