package com.irrigation.presentation.controller;

import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageOutput;
import com.irrigation.application.service.CropService;
import com.irrigation.presentation.mapper.CropDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
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
 * API de cultura com KC e Z e zero estágios (Req 2.4, 2.7, 2.10 e 2.11 do módulo de balanço hídrico).
 * Complementa o {@link CropControllerTest}, sem repetir a revisão A-01 (kc e rootDepthMm ausentes).
 */
@WebMvcTest(CropController.class)
@Import(CropDtoMapperImpl.class)
class CropOptionalFieldsControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final String MINIMAL_BODY = "{\"name\":\"Milho\",\"kc\":1.15,\"rootDepthMm\":450.50}";

    @Autowired
    MockMvc mvc;

    @MockBean
    CropService service;

    private static CropOutput minimalOutput(UUID id) {
        return new CropOutput(id, "Milho", null, null, List.of(), new BigDecimal("1.15"), new BigDecimal("450.50"));
    }

    @Test
    void createWithOnlyNameKcAndRootDepthReturns201AndMapsRootDepthMmToTheService() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(minimalOutput(id));

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(MINIMAL_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.kc").value(1.15))
                .andExpect(jsonPath("$.rootDepthMm").value(450.5))
                .andExpect(jsonPath("$.stages.length()").value(0));

        ArgumentCaptor<CropInput> captor = ArgumentCaptor.forClass(CropInput.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().kc()).isEqualByComparingTo("1.15");
        assertThat(captor.getValue().rootDepth()).isEqualByComparingTo("450.50");
        assertThat(captor.getValue().stages()).isNullOrEmpty();
    }

    @Test
    void updateWithOnlyNameKcAndRootDepthReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(minimalOutput(id));

        mvc.perform(put("/api/v1/crops/{id}", id).contentType(MediaType.APPLICATION_JSON).content(MINIMAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootDepthMm").value(450.5));
    }

    @Test
    void createRejectsKcAndRootDepthOutOfRangeNamingEachField() throws Exception {
        String body = "{\"name\":\"Milho\",\"kc\":2.01,\"rootDepthMm\":0}";

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("kc", "rootDepthMm")));
    }

    @Test
    void createRejectsMoreThanTwentyStagesNamingTheStagesField() throws Exception {
        StringBuilder stages = new StringBuilder();
        for (int i = 1; i <= 21; i++) {
            stages.append(i > 1 ? "," : "").append("{\"name\":\"E").append(i).append("\",\"kc\":1.0,\"stageOrder\":")
                    .append(i).append("}");
        }
        String body = "{\"name\":\"Milho\",\"kc\":1.0,\"rootDepthMm\":400,\"stages\":[" + stages + "]}";

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("stages")));
    }

    @Test
    void getOfALegacyCropReturnsNullKcAndRootDepthAndKeepsTheStoredValues() throws Exception {
        UUID id = UUID.randomUUID();
        // Construtor de compatibilidade: registro anterior ao módulo, sem KC e Z.
        when(service.getById(id)).thenReturn(new CropOutput(id, "Milho", "Híbrido", 120,
                List.of(new CropStageOutput("Inicial", new BigDecimal("0.40"), 1))));

        mvc.perform(get("/api/v1/crops/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kc").value(nullValue()))
                .andExpect(jsonPath("$.rootDepthMm").value(nullValue()))
                .andExpect(jsonPath("$.name").value("Milho"))
                .andExpect(jsonPath("$.variety").value("Híbrido"))
                .andExpect(jsonPath("$.cultivationCycleDays").value(120))
                .andExpect(jsonPath("$.stages[0].kc").value(0.4));
    }
}
