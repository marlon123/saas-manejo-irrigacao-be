package com.irrigation.presentation.controller;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.application.service.AppliedIrrigationService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.presentation.mapper.AppliedIrrigationDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppliedIrrigationController.class)
@Import(AppliedIrrigationDtoMapperImpl.class)
class AppliedIrrigationControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final UUID AREA = UUID.randomUUID();
    private static final String BASE = "/api/v1/areas/{areaId}/applied-irrigation";
    private static final String BODY = """
            {"date":"2026-04-10","appliedIrrigationMm":12.50}""";

    @Autowired
    MockMvc mvc;

    @MockBean
    AppliedIrrigationService service;

    private static AppliedIrrigationOutput output(String date, String mm) {
        return new AppliedIrrigationOutput(UUID.randomUUID(), AREA, LocalDate.parse(date), new BigDecimal(mm));
    }

    @Test
    void upsertReturns200AndMapsDateFromBody() throws Exception {
        when(service.upsert(eq(AREA), any())).thenReturn(output("2026-04-10", "12.50"));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(AREA.toString()))
                .andExpect(jsonPath("$.date").value("2026-04-10"))
                .andExpect(jsonPath("$.appliedIrrigationMm").value(12.50));

        ArgumentCaptor<AppliedIrrigationInput> captor = ArgumentCaptor.forClass(AppliedIrrigationInput.class);
        verify(service).upsert(eq(AREA), captor.capture());
        assertThat(captor.getValue().date()).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(captor.getValue().appliedIrrigationMm()).isEqualByComparingTo("12.50");
    }

    @Test
    void upsertAcceptsZeroDepth() throws Exception {
        when(service.upsert(eq(AREA), any())).thenReturn(output("2026-04-10", "0.00"));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\",\"appliedIrrigationMm\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedIrrigationMm").value(0.0));

        ArgumentCaptor<AppliedIrrigationInput> captor = ArgumentCaptor.forClass(AppliedIrrigationInput.class);
        verify(service).upsert(eq(AREA), captor.capture());
        assertThat(captor.getValue().appliedIrrigationMm()).isEqualByComparingTo("0");
    }

    @Test
    void upsertReportsAllInvalidFieldsInViolations() throws Exception {
        when(service.upsert(eq(AREA), any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("date", "obrigatória"),
                new FieldViolation("appliedIrrigationMm", "obrigatória"))));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations.length()").value(2))
                .andExpect(jsonPath("$.violations[*].field", hasItems("date", "appliedIrrigationMm")));
    }

    @Test
    void upsertRejectsMissingMalformedBodyAndInvalidTypes() throws Exception {
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-13-45\",\"appliedIrrigationMm\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("date"));
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\",\"appliedIrrigationMm\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("appliedIrrigationMm"));
    }

    @Test
    void upsertReturns404ForUnknownArea() throws Exception {
        when(service.upsert(eq(AREA), any())).thenThrow(new EntityNotFoundException("Área", AREA));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void upsertReturns422WhenBusinessRuleIsViolated() throws Exception {
        when(service.upsert(eq(AREA), any())).thenThrow(new BusinessRuleException("Data futura não é permitida"));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listPassesPeriodAndKeepsServiceOrder() throws Exception {
        when(service.findByPeriod(AREA, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")))
                .thenReturn(List.of(output("2026-04-01", "10.00"), output("2026-04-02", "0.00")));

        mvc.perform(get(BASE, AREA).param("from", "2026-04-01").param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-04-01"))
                .andExpect(jsonPath("$[1].date").value("2026-04-02"));
    }

    @Test
    void listReturnsEmptyArrayWhenNoRecords() throws Exception {
        when(service.findByPeriod(eq(AREA), any(), any())).thenReturn(List.of());

        mvc.perform(get(BASE, AREA).param("from", "2026-04-01").param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listRejectsInvalidPeriodFromService() throws Exception {
        when(service.findByPeriod(eq(AREA), any(), any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("from", "deve ser anterior ou igual a 'to'"))));

        mvc.perform(get(BASE, AREA).param("from", "2026-05-01").param("to", "2026-04-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("from"));
    }

    @Test
    void listRejectsMissingPeriodParameters() throws Exception {
        when(service.findByPeriod(AREA, null, null)).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("from", "obrigatória"), new FieldViolation("to", "obrigatória"))));

        mvc.perform(get(BASE, AREA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("from", "to")));
    }

    @Test
    void listRejectsInvalidDateFormat() throws Exception {
        mvc.perform(get(BASE, AREA).param("from", "01/04/2026").param("to", "2026-04-30"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("from"));
        mvc.perform(get(BASE, AREA).param("from", "2026-04-01").param("to", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("to"));
    }

    @Test
    void listReturns404ForUnknownArea() throws Exception {
        when(service.findByPeriod(eq(AREA), any(), any())).thenThrow(new EntityNotFoundException("Área", AREA));

        mvc.perform(get(BASE, AREA).param("from", "2026-04-01").param("to", "2026-04-30"))
                .andExpect(status().isNotFound());
    }
}
