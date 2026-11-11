package com.irrigation.presentation.controller;

import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.CalculationResultOutput;
import com.irrigation.application.dto.RecommendationOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.service.RecommendationService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.presentation.mapper.RecommendationDtoMapperImpl;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(RecommendationDtoMapperImpl.class)
class RecommendationControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final UUID AREA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    MockMvc mvc;

    @MockBean
    RecommendationService service;

    private static String calculateBody() {
        return """
                {"areaId":"%s","methodologyId":"fake",
                 "inputs":{"eto":5,"kc":1.0,"etc":5,"effectivePrecipitation":0,"currentSoilMoisture":50,
                           "waterStorageCapacity":100,"irrigationEfficiency":0.8,"netIrrigationDepth":20,
                           "grossIrrigationDepth":25,"irrigatedArea":10,"systemFlowRate":100,"irrigationTime":2}}"""
                .formatted(AREA_ID);
    }

    private static RecommendationOutput output(UUID id) {
        return new RecommendationOutput(id, AREA_ID, LocalDateTime.of(2025, 3, 1, 12, 0),
                CalculationParameters.from(TestData.calculationInput()),
                new CalculationResultOutput(new BigDecimal("20"), new BigDecimal("2000"), new BigDecimal("2")),
                "fake", "1.0");
    }

    // -------------------------------------------------------------- calculate

    @Test
    void calculateReturns201WithTheStoredRecommendation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.calculate(any())).thenReturn(output(id));

        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(calculateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/recommendations/" + id)))
                .andExpect(jsonPath("$.areaId").value(AREA_ID.toString()))
                .andExpect(jsonPath("$.methodologyVersion").value("1.0"))
                .andExpect(jsonPath("$.results.irrigationDepth").value(20))
                .andExpect(jsonPath("$.inputs.kc").value(1.0))
                .andExpect(jsonPath("$.dateTime").value("2025-03-01T12:00:00"));
    }

    @Test
    void calculateRejectsAMissingAreaAndMethodology() throws Exception {
        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("areaId", "methodologyId", "inputs")));
    }

    @Test
    void calculateReturns400IdentifyingInvalidCalculationParameters() throws Exception {
        when(service.calculate(any())).thenThrow(new InvalidCalculationInputException(List.of("ETo", "Kc")));

        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(calculateBody()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("ETo", "Kc")));
    }

    @Test
    void calculateReturns422WhenTheMethodologyIsNotRegistered() throws Exception {
        when(service.calculate(any())).thenThrow(new MethodologyNotAvailableException(new MethodologyIdentifier("fake")));

        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(calculateBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value(containsString("fake")));
    }

    @Test
    void calculateReturns404ForAnUnknownArea() throws Exception {
        when(service.calculate(any())).thenThrow(new EntityNotFoundException("Área", AREA_ID));

        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(calculateBody()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ------------------------------------------------------------------- list

    @Test
    void listForwardsAreaPeriodAndPagination() throws Exception {
        when(service.list(any(), any(), any(), any(), any()))
                .thenReturn(PagedResult.of(List.of(output(UUID.randomUUID())), 1, 10, 11));

        mvc.perform(get("/api/v1/recommendations?areaId={a}&dateFrom=2025-01-01T00:00:00&dateTo=2025-02-01T00:00:00"
                        + "&page=1&size=10", AREA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(service).list(eq(AREA_ID), eq(LocalDateTime.of(2025, 1, 1, 0, 0)),
                eq(LocalDateTime.of(2025, 2, 1, 0, 0)), eq(1), eq(10));
    }

    @Test
    void listWithNoMatchReturnsAnEmptyPageWith200() throws Exception {
        when(service.list(any(), any(), any(), any(), any())).thenReturn(PagedResult.of(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listRejectsAnInvalidDateAndAnInvalidAreaId() throws Exception {
        mvc.perform(get("/api/v1/recommendations?dateFrom=ontem"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("dateFrom"));
        mvc.perform(get("/api/v1/recommendations?areaId=xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("areaId"));
    }

    @Test
    void listSurfacesAnInvertedPeriodReportedByTheService() throws Exception {
        when(service.list(any(), any(), any(), any(), any())).thenThrow(new ValidationFailedException(
                List.of(new FieldViolation("dateFrom", "não pode ser posterior a dateTo"))));

        mvc.perform(get("/api/v1/recommendations?dateFrom=2025-02-01T00:00:00&dateTo=2025-01-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("dateFrom"));
    }

    // -------------------------------------------------------------------- get

    @Test
    void getReturnsOr404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(output(id));
        UUID unknown = UUID.randomUUID();
        when(service.getById(unknown)).thenThrow(new EntityNotFoundException("Recomendação", unknown));

        mvc.perform(get("/api/v1/recommendations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(get("/api/v1/recommendations/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void recommendationsAreImmutableSoThereIsNoUpdateOrDelete() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/recommendations/{id}", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/recommendations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
