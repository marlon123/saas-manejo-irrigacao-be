package com.irrigation.presentation.controller;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.service.AreaService;
import com.irrigation.presentation.mapper.AreaDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de área com campos opcionais (Req 3.1 a 3.4 do módulo de balanço hídrico).
 * Complementa o {@link AreaControllerTest}.
 */
@WebMvcTest(AreaController.class)
@Import(AreaDtoMapperImpl.class)
class AreaOptionalFieldsControllerTest {

    private static final String PROBLEM = "application/problem+json";

    @Autowired
    MockMvc mvc;

    @MockBean
    AreaService service;

    private static AreaOutput onlyName(UUID id) {
        return new AreaOutput(id, "Talhão 2", null, null, null, null, null, null, null);
    }

    @Test
    void createWithOnlyANameReturns201AndPassesAbsentFieldsAsNull() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(onlyName(id));

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Talhão 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Talhão 2"))
                .andExpect(jsonPath("$.areaHectares").value(nullValue()))
                .andExpect(jsonPath("$.irrigationSystemType").value(nullValue()))
                .andExpect(jsonPath("$.systemFlowRate").value(nullValue()));

        ArgumentCaptor<AreaInput> captor = ArgumentCaptor.forClass(AreaInput.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().areaHectares()).isNull();
        assertThat(captor.getValue().irrigationSystemType()).isNull();
        assertThat(captor.getValue().systemFlowRate()).isNull();
    }

    @Test
    void updateWithOnlyANameReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(onlyName(id));

        mvc.perform(put("/api/v1/areas/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Talhão 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaHectares").value(nullValue()));
    }

    @Test
    void createRejectsABlankNameAndPresentValuesOutOfRange() throws Exception {
        String body = "{\"name\":\"\",\"areaHectares\":1000000.01,\"systemFlowRate\":0}";

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("name", "areaHectares", "systemFlowRate")));
    }

    @Test
    void getOfAnAreaWithAbsentFieldsKeepsNullsAndStoredValues() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(new AreaOutput(id, "Talhão 3", new BigDecimal("10.50"), null, null,
                "solo argiloso", null, null, null));

        mvc.perform(get("/api/v1/areas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaHectares").value(10.5))
                .andExpect(jsonPath("$.generalCharacteristics").value("solo argiloso"))
                .andExpect(jsonPath("$.irrigationSystemType").value(nullValue()))
                .andExpect(jsonPath("$.systemFlowRate").value(nullValue()));
    }
}
