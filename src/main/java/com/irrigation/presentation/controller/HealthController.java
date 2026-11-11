package com.irrigation.presentation.controller;

import com.irrigation.application.health.DatabaseHealth;
import com.irrigation.presentation.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de saúde da aplicação e do banco de dados (Req 9.1–9.3).
 *
 * <p>Responde sempre 200: se a aplicação responde, ela está "healthy"; com o banco
 * inacessível o estado geral passa a "degraded". Para sondas de orquestração que
 * dependem do código HTTP, use {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Saúde", description = "Estado da aplicação e do banco de dados")
public class HealthController {

    private final DatabaseHealth databaseHealth;

    public HealthController(DatabaseHealth databaseHealth) {
        this.databaseHealth = databaseHealth;
    }

    @GetMapping
    @Operation(summary = "Informa o estado da aplicação e a conectividade com o banco",
            description = "`status` é `healthy` ou `degraded` (banco inacessível).")
    public HealthResponse health() {
        boolean connected = databaseHealth.isConnected();
        return new HealthResponse(
                connected ? "healthy" : "degraded",
                "healthy",
                connected ? "connected" : "disconnected");
    }
}
