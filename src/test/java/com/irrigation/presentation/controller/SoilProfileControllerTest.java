package com.irrigation.presentation.controller;

import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.service.SoilProfileService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.presentation.mapper.SoilProfileDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SoilProfileController.class)
@Import(SoilProfileDtoMapperImpl.class)
class SoilProfileControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final String VALID_BODY = """
            {"soilType":"Argiloso","fieldCapacity":0.35,"permanentWiltingPoint":0.18,"bulkDensity":1.30,
             "effectiveRootDepth":600.00,"availableWater":100.00,
             "soilWaterUnit":"VOLUMETRIC_WATER_CONTENT","bulkDensityUnit":"GRAMS_PER_CUBIC_CENTIMETER",
             "rootDepthUnit":"MILLIMETERS","availableWaterUnit":"MILLIMETERS"}""";

    @Autowired
    MockMvc mvc;

    @MockBean
    SoilProfileService service;

    private static SoilProfileOutput output(UUID id) {
        return new SoilProfileOutput(id, "Argiloso", new BigDecimal("0.35"), new BigDecimal("0.18"),
                new BigDecimal("1.30"), new BigDecimal("600.00"), new BigDecimal("100.00"),
                MeasurementUnit.VOLUMETRIC_WATER_CONTENT, MeasurementUnit.GRAMS_PER_CUBIC_CENTIMETER,
                MeasurementUnit.MILLIMETERS, MeasurementUnit.MILLIMETERS);
    }

    @Test
    void createReturns201WithExplicitUnits() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(output(id));

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/soil-profiles/" + id)))
                .andExpect(jsonPath("$.bulkDensityUnit").value("GRAMS_PER_CUBIC_CENTIMETER"))
                .andExpect(jsonPath("$.rootDepthUnit").value("MILLIMETERS"));
    }

    @Test
    void createRejectsOutOfRangeValuesAndMissingUnits() throws Exception {
        String body = """
                {"soilType":"","fieldCapacity":1.5,"permanentWiltingPoint":0.1,"bulkDensity":0,
                 "effectiveRootDepth":9999,"availableWater":-1}""";

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems(
                        "soilType", "fieldCapacity", "bulkDensity", "effectiveRootDepth", "availableWater",
                        "soilWaterUnit", "bulkDensityUnit", "rootDepthUnit", "availableWaterUnit")));
    }

    @Test
    void createSurfacesTheFieldCapacityRuleFromTheService() throws Exception {
        when(service.create(any())).thenThrow(new ValidationFailedException(List.of(new FieldViolation(
                "fieldCapacity", "capacidade de campo deve ser maior que o ponto de murcha permanente"))));

        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("fieldCapacity"));
    }

    @Test
    void listReturnsThePage() throws Exception {
        when(service.list(any(), any())).thenReturn(PagedResult.of(List.of(output(UUID.randomUUID())), 0, 20, 1));

        mvc.perform(get("/api/v1/soil-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReturnsOr404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(output(id));
        UUID unknown = UUID.randomUUID();
        when(service.getById(unknown)).thenThrow(new EntityNotFoundException("Perfil de solo", unknown));

        mvc.perform(get("/api/v1/soil-profiles/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/soil-profiles/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns200Or404Or400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(output(id));
        UUID unknown = UUID.randomUUID();
        when(service.update(eq(unknown), any())).thenThrow(new EntityNotFoundException("Perfil de solo", unknown));

        mvc.perform(put("/api/v1/soil-profiles/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/soil-profiles/{id}", unknown).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/soil-profiles/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204Or404() throws Exception {
        UUID unknown = UUID.randomUUID();
        Mockito.doThrow(new EntityNotFoundException("Perfil de solo", unknown)).when(service).delete(unknown);

        mvc.perform(delete("/api/v1/soil-profiles/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/soil-profiles/{id}", unknown)).andExpect(status().isNotFound());
    }
}
