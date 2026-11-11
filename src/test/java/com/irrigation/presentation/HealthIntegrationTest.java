package com.irrigation.presentation;

import com.irrigation.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saúde com o PostgreSQL no ar (Req 9.3). O cenário de queda está em DatabaseDownHealthIntegrationTest. */
class HealthIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void reportsHealthyAndConnected() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.application").value("healthy"))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    void actuatorReportsUpWithTheDatabaseComponent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}
