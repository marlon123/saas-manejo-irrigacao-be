package com.irrigation.presentation.controller;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.service.SoilProfileService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.presentation.mapper.SoilProfileDtoMapperImpl;
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
 * API de solo com campos opcionais e umidade gravimétrica (Req 1.4, 1.5, 1.8, 1.9 e 1.11 do módulo de
 * balanço hídrico). Complementa o {@link SoilProfileControllerTest}.
 */
@WebMvcTest(SoilProfileController.class)
@Import(SoilProfileDtoMapperImpl.class)
class SoilProfileOptionalFieldsControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final String MINIMAL_BODY = """
            {"soilType":"Argiloso","fieldCapacityGravimetricPct":28.50,
             "permanentWiltingPointGravimetricPct":14.20,"bulkDensity":1.30}""";

    @Autowired
    MockMvc mvc;

    @MockBean
    SoilProfileService service;

    private static SoilProfileOutput minimalOutput(UUID id) {
        return new SoilProfileOutput(id, "Argiloso", null, null, new BigDecimal("28.50"), new BigDecimal("14.20"),
                new BigDecimal("1.30"), null, null, null, null, null, null);
    }

    @Test
    void createWithOnlyTypeGravimetricPairAndDensityReturns201AndMapsTheFieldsToTheService() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(minimalOutput(id));

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON).content(MINIMAL_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(28.5))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(14.2))
                .andExpect(jsonPath("$.bulkDensity").value(1.3))
                .andExpect(jsonPath("$.fieldCapacity").value(nullValue()))
                .andExpect(jsonPath("$.effectiveRootDepth").value(nullValue()))
                .andExpect(jsonPath("$.bulkDensityUnit").value(nullValue()));

        ArgumentCaptor<SoilProfileInput> captor = ArgumentCaptor.forClass(SoilProfileInput.class);
        verify(service).create(captor.capture());
        SoilProfileInput input = captor.getValue();
        assertThat(input.fieldCapacityGravimetric()).isEqualByComparingTo("28.50");
        assertThat(input.permanentWiltingPointGravimetric()).isEqualByComparingTo("14.20");
        assertThat(input.bulkDensity()).isEqualByComparingTo("1.30");
        assertThat(input.fieldCapacity()).isNull();
        assertThat(input.permanentWiltingPoint()).isNull();
    }

    @Test
    void updateWithOnlyTypeGravimetricPairAndDensityReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(minimalOutput(id));

        mvc.perform(put("/api/v1/soil-profiles/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(28.5))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(14.2));
    }

    @Test
    void createRejectsAMissingBulkDensityAndGravimetricValuesOutOfRange() throws Exception {
        String body = """
                {"soilType":"Argiloso","fieldCapacityGravimetricPct":100.01,
                 "permanentWiltingPointGravimetricPct":-0.01}""";

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems(
                        "fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct", "bulkDensity")));
    }

    @Test
    void createSurfacesTheIncompleteVolumetricPairNamingBothFields() throws Exception {
        String message = "par incompleto: fieldCapacity e permanentWiltingPoint devem ser informados em conjunto";
        when(service.create(any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("fieldCapacity", message), new FieldViolation("permanentWiltingPoint", message))));

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Argiloso\",\"fieldCapacity\":0.35,\"bulkDensity\":1.30}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("fieldCapacity", "permanentWiltingPoint")));
    }

    @Test
    void createSurfacesTheIncompleteGravimetricPairAndTheRelationRuleFromTheService() throws Exception {
        when(service.create(any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("fieldCapacityGravimetricPct", "par incompleto"),
                new FieldViolation("permanentWiltingPointGravimetricPct", "par incompleto"))));

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Argiloso\",\"fieldCapacityGravimetricPct\":28.5,\"bulkDensity\":1.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems(
                        "fieldCapacityGravimetricPct", "permanentWiltingPointGravimetricPct")));
    }

    @Test
    void getOfALegacySoilReturnsNullGravimetricValuesAndKeepsTheStoredOnes() throws Exception {
        UUID id = UUID.randomUUID();
        // Construtor de compatibilidade: registro anterior ao módulo, sem o par gravimétrico.
        when(service.getById(id)).thenReturn(new SoilProfileOutput(id, "Argiloso", new BigDecimal("0.35"),
                new BigDecimal("0.18"), new BigDecimal("1.30"), new BigDecimal("600.00"), new BigDecimal("100.00"),
                null, null, null, null));

        mvc.perform(get("/api/v1/soil-profiles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(nullValue()))
                .andExpect(jsonPath("$.fieldCapacity").value(0.35))
                .andExpect(jsonPath("$.permanentWiltingPoint").value(0.18))
                .andExpect(jsonPath("$.bulkDensity").value(1.3))
                .andExpect(jsonPath("$.effectiveRootDepth").value(600.0))
                .andExpect(jsonPath("$.availableWater").value(100.0));
    }
}
