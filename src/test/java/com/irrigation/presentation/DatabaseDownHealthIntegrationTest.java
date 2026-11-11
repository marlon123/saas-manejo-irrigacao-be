package com.irrigation.presentation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saúde quando o banco fica inacessível depois da inicialização (Req 9.3).
 *
 * <p>Usa um container próprio, parado durante o teste, e um contexto descartado ao fim,
 * para não afetar o banco compartilhado dos demais testes de integração. O timeout curto
 * do pool evita esperar os 30 s padrão do Hikari.
 */
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseDownHealthIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void reportsDegradedAndDisconnectedOnceTheDatabaseGoesDown() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(jsonPath("$.database").value("connected"));

        POSTGRES.stop();

        // A API continua respondendo 200, com o estado degradado (decisão 9 do handoff)
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.application").value("healthy"))
                .andExpect(jsonPath("$.database").value("disconnected"));

        // Para sondas de orquestração, o Actuator sinaliza a indisponibilidade com 503
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }
}
