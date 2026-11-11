package com.irrigation.presentation.controller;

import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageOutput;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.service.CropService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.presentation.mapper.CropDtoMapperImpl;
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

@WebMvcTest(CropController.class)
@Import(CropDtoMapperImpl.class)
class CropControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final String VALID_BODY = """
            {"name":"Milho","variety":"Híbrido","cultivationCycleDays":120,
             "stages":[{"name":"Inicial","kc":0.40,"stageOrder":1},
                       {"name":"Pleno","kc":1.15,"stageOrder":2}]}""";

    @Autowired
    MockMvc mvc;

    @MockBean
    CropService service;

    private static CropOutput output(UUID id) {
        return new CropOutput(id, "Milho", "Híbrido", 120, List.of(
                new CropStageOutput("Inicial", new BigDecimal("0.40"), 1),
                new CropStageOutput("Pleno", new BigDecimal("1.15"), 2)));
    }

    @Test
    void createReturns201WithTheStages() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(output(id));

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/crops/" + id)))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[1].kc").value(1.15));
    }

    @Test
    void createRejectsInvalidStagesNamingEachField() throws Exception {
        String body = """
                {"name":"Milho","stages":[{"name":"","kc":3.0,"stageOrder":0}]}""";

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field",
                        hasItems("stages[0].name", "stages[0].kc", "stages[0].stageOrder")));
    }

    @Test
    void createRejectsAMissingStageList() throws Exception {
        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Milho\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("stages")));
    }

    @Test
    void createSurfacesServiceViolations() throws Exception {
        when(service.create(any())).thenThrow(new ValidationFailedException(
                List.of(new FieldViolation("stages", "ordem do estágio deve ser única dentro da cultura: 1"))));

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("stages"));
    }

    @Test
    void listReturnsThePage() throws Exception {
        when(service.list(any(), any())).thenReturn(PagedResult.of(List.of(output(UUID.randomUUID())), 0, 20, 1));

        mvc.perform(get("/api/v1/crops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listRejectsInvalidPagination() throws Exception {
        when(service.list(any(), any())).thenThrow(new ValidationFailedException(
                List.of(new FieldViolation("page", "deve ser maior ou igual a zero"))));

        mvc.perform(get("/api/v1/crops?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturnsOr404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(output(id));
        UUID unknown = UUID.randomUUID();
        when(service.getById(unknown)).thenThrow(new EntityNotFoundException("Cultura", unknown));

        mvc.perform(get("/api/v1/crops/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/crops/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns200Or404Or400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(output(id));
        UUID unknown = UUID.randomUUID();
        when(service.update(eq(unknown), any())).thenThrow(new EntityNotFoundException("Cultura", unknown));

        mvc.perform(put("/api/v1/crops/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/crops/{id}", unknown).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/crops/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204Or404() throws Exception {
        UUID unknown = UUID.randomUUID();
        Mockito.doThrow(new EntityNotFoundException("Cultura", unknown)).when(service).delete(unknown);

        mvc.perform(delete("/api/v1/crops/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/crops/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }
}
