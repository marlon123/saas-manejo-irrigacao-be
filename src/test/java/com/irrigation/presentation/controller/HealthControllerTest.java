package com.irrigation.presentation.controller;

import com.irrigation.application.health.DatabaseHealth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    DatabaseHealth databaseHealth;

    @Test
    void reportsHealthyWhenTheDatabaseIsConnected() throws Exception {
        when(databaseHealth.isConnected()).thenReturn(true);

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.application").value("healthy"))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    void reportsDegradedWhenTheDatabaseIsUnreachable() throws Exception {
        when(databaseHealth.isConnected()).thenReturn(false);

        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.application").value("healthy"))
                .andExpect(jsonPath("$.database").value("disconnected"));
    }
}
