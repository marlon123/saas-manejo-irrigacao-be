package com.irrigation.presentation.controller;

import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.service.AreaService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.presentation.mapper.AreaDtoMapperImpl;
import org.junit.jupiter.api.Test;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AreaController.class)
@Import(AreaDtoMapperImpl.class)
class AreaControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final String VALID_BODY = """
            {"name":"Talhão 1","areaHectares":10.5,"latitude":-23.5,"longitude":-46.6,
             "generalCharacteristics":"solo argiloso","irrigationSystemType":"DRIP",
             "systemFlowRate":120.5,"irrigationEfficiency":0.85}""";

    @Autowired
    MockMvc mvc;

    @MockBean
    AreaService service;

    private static AreaOutput output(UUID id) {
        return new AreaOutput(id, "Talhão 1", new BigDecimal("10.50"), new BigDecimal("-23.5"),
                new BigDecimal("-46.6"), "solo argiloso", IrrigationSystemType.DRIP,
                new BigDecimal("120.50"), new BigDecimal("0.85"));
    }

    // ------------------------------------------------------------------ POST

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(output(id));

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/areas/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Talhão 1"))
                .andExpect(jsonPath("$.irrigationSystemType").value("DRIP"));
    }

    @Test
    void createRejectsInvalidDataWithProblemDetailsListingEachField() throws Exception {
        String body = """
                {"name":"  ","areaHectares":0,"latitude":95,"irrigationSystemType":null,"systemFlowRate":-1}""";

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.violations[*].field",
                        hasItems("name", "areaHectares", "latitude", "irrigationSystemType", "systemFlowRate")))
                .andExpect(jsonPath("$.violations[*].message").isNotEmpty());
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content("{ isto nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value(not(containsString("com.fasterxml"))));
    }

    @Test
    void createRejectsAnUnknownEnumValueNamingTheField() throws Exception {
        String body = VALID_BODY.replace("DRIP", "TELEPORTE");

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItem("irrigationSystemType")));
    }

    @Test
    void createSurfacesDomainViolationsFromTheService() throws Exception {
        when(service.create(any())).thenThrow(new ValidationFailedException(
                List.of(new FieldViolation("geographicLocation", "latitude e longitude devem ser informadas em conjunto"))));

        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("geographicLocation"));
    }

    // ------------------------------------------------------------------- GET

    @Test
    void listReturnsThePageWithMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.list(any(), any(), any(), any()))
                .thenReturn(PagedResult.of(List.of(output(id)), 0, 20, 1));

        mvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listForwardsPaginationAndSortParameters() throws Exception {
        when(service.list(eq(2), eq(5), eq("areaHectares"), eq("desc")))
                .thenReturn(PagedResult.of(List.of(), 2, 5, 0));

        mvc.perform(get("/api/v1/areas?page=2&size=5&sortBy=areaHectares&direction=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void listRejectsInvalidParametersReportedByTheService() throws Exception {
        when(service.list(any(), any(), any(), any())).thenThrow(new ValidationFailedException(
                List.of(new FieldViolation("size", "deve estar entre 1 e 100"))));

        mvc.perform(get("/api/v1/areas?size=500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    @Test
    void listRejectsANonNumericPageSize() throws Exception {
        mvc.perform(get("/api/v1/areas?size=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    @Test
    void getReturnsTheArea() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(output(id));

        mvc.perform(get("/api/v1/areas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getReturns404ProblemDetailsForAnUnknownArea() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new EntityNotFoundException("Área", id));

        mvc.perform(get("/api/v1/areas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString(id.toString())));
    }

    @Test
    void getRejectsAMalformedIdentifier() throws Exception {
        mvc.perform(get("/api/v1/areas/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ------------------------------------------------------------------- PUT

    @Test
    void updateReturnsTheUpdatedArea() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(output(id));

        mvc.perform(put("/api/v1/areas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateReturns404ForAnUnknownArea() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenThrow(new EntityNotFoundException("Área", id));

        mvc.perform(put("/api/v1/areas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateRejectsInvalidData() throws Exception {
        mvc.perform(put("/api/v1/areas/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItem("name")));
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/areas/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deleteReturns404ForAnUnknownArea() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new EntityNotFoundException("Área", id)).when(service).delete(id);

        mvc.perform(delete("/api/v1/areas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void deleteReturns422WhenTheAreaIsReferenced() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new BusinessRuleException("A área está referenciada por recomendações"))
                .when(service).delete(id);

        mvc.perform(delete("/api/v1/areas/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("referenciada")));
    }

    // ---------------------------------------------------------- erros gerais

    @Test
    void unexpectedErrorsReturnAGeneric500WithoutLeakingDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new IllegalStateException("senha-do-banco=segredo"));

        mvc.perform(get("/api/v1/areas/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(content().string(not(containsString("segredo"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void unsupportedMethodsReturnProblemDetails() throws Exception {
        mvc.perform(patch("/api/v1/areas/{id}", UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }
}
