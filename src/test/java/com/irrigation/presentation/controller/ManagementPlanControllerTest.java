package com.irrigation.presentation.controller;

import com.irrigation.application.dto.ManagementPlanInput;
import com.irrigation.application.dto.ManagementPlanOutput;
import com.irrigation.application.service.ManagementPlanService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.presentation.mapper.ManagementPlanDtoMapperImpl;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagementPlanController.class)
@Import(ManagementPlanDtoMapperImpl.class)
class ManagementPlanControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final UUID AREA = UUID.randomUUID();
    private static final UUID CROP = UUID.randomUUID();
    private static final UUID SOIL = UUID.randomUUID();
    private static final String BASE = "/api/v1/areas/{areaId}/management-plans";

    @Autowired
    MockMvc mvc;

    @MockBean
    ManagementPlanService service;

    private static String body() {
        return """
                {"cropId":"%s","soilProfileId":"%s","startDate":"2026-03-01","endDate":"2026-08-31",
                 "fFactor":0.50,"sprinklerRateMmPerHour":5.00}""".formatted(CROP, SOIL);
    }

    private static ManagementPlanOutput output(UUID id, LocalDate start, LocalDate end) {
        return new ManagementPlanOutput(id, AREA, CROP, SOIL, start, end,
                new BigDecimal("0.50"), new BigDecimal("5.00"));
    }

    @Test
    void createReturns201WithLocationAndMapsAllFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(eq(AREA), any())).thenReturn(
                output(id, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-08-31")));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(
                        "/api/v1/areas/" + AREA + "/management-plans/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.areaId").value(AREA.toString()))
                .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.endDate").value("2026-08-31"))
                .andExpect(jsonPath("$.fFactor").value(0.50))
                .andExpect(jsonPath("$.sprinklerRateMmPerHour").value(5.00));

        ArgumentCaptor<ManagementPlanInput> captor = ArgumentCaptor.forClass(ManagementPlanInput.class);
        verify(service).create(eq(AREA), captor.capture());
        ManagementPlanInput in = captor.getValue();
        assertThat(in.cropId()).isEqualTo(CROP);
        assertThat(in.soilProfileId()).isEqualTo(SOIL);
        assertThat(in.startDate()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(in.endDate()).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(in.fFactor()).isEqualByComparingTo("0.50");
        assertThat(in.sprinklerRateMmPerHour()).isEqualByComparingTo("5.00");
    }

    @Test
    void createWithoutEndDateSendsNullEndDate() throws Exception {
        when(service.create(eq(AREA), any())).thenReturn(output(UUID.randomUUID(), LocalDate.parse("2026-03-01"), null));
        String active = """
                {"cropId":"%s","soilProfileId":"%s","startDate":"2026-03-01","fFactor":0.5,
                 "sprinklerRateMmPerHour":5}""".formatted(CROP, SOIL);

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(active))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").doesNotExist());

        ArgumentCaptor<ManagementPlanInput> captor = ArgumentCaptor.forClass(ManagementPlanInput.class);
        verify(service).create(eq(AREA), captor.capture());
        assertThat(captor.getValue().endDate()).isNull();
    }

    @Test
    void createReportsAllInvalidFieldsInViolations() throws Exception {
        when(service.create(eq(AREA), any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("cropId", "obrigatório"),
                new FieldViolation("soilProfileId", "obrigatório"),
                new FieldViolation("startDate", "obrigatória"),
                new FieldViolation("fFactor", "deve ser maior que 0 e até 1"),
                new FieldViolation("sprinklerRateMmPerHour", "deve ser maior que 0"))));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations.length()").value(5))
                .andExpect(jsonPath("$.violations[*].field", hasItems(
                        "cropId", "soilProfileId", "startDate", "fFactor", "sprinklerRateMmPerHour")));
    }

    @Test
    void createRejectsMissingOrMalformedBody() throws Exception {
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createRejectsInvalidDateFormatInBody() throws Exception {
        String invalid = """
                {"cropId":"%s","soilProfileId":"%s","startDate":"01/03/2026","fFactor":0.5,
                 "sprinklerRateMmPerHour":5}""".formatted(CROP, SOIL);

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("startDate"));
    }

    @Test
    void createRejectsNonNumericFFactor() throws Exception {
        String invalid = """
                {"cropId":"%s","soilProfileId":"%s","startDate":"2026-03-01","fFactor":"abc",
                 "sprinklerRateMmPerHour":5}""".formatted(CROP, SOIL);

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("fFactor"));
    }

    @Test
    void createReturns404ForUnknownAreaCropOrSoil() throws Exception {
        when(service.create(eq(AREA), any())).thenThrow(new EntityNotFoundException("Área", AREA));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns422WhenBusinessRuleIsViolated() throws Exception {
        when(service.create(eq(AREA), any())).thenThrow(
                new BusinessRuleException("A área já possui um plano ativo ou o período se sobrepõe a outro plano"));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value(containsString("plano")));
    }

    @Test
    void createReturns409OnConflict() throws Exception {
        when(service.create(eq(AREA), any())).thenThrow(new ConflictException("Processamento em andamento"));

        mvc.perform(post(BASE, AREA).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturnsPlansInServiceOrder() throws Exception {
        UUID newer = UUID.randomUUID();
        UUID older = UUID.randomUUID();
        when(service.listByArea(AREA)).thenReturn(List.of(
                output(newer, LocalDate.parse("2026-03-01"), null),
                output(older, LocalDate.parse("2025-03-01"), LocalDate.parse("2025-08-31"))));

        mvc.perform(get(BASE, AREA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.toString()))
                .andExpect(jsonPath("$[1].id").value(older.toString()));
    }

    @Test
    void listReturnsEmptyArrayWhenAreaHasNoPlans() throws Exception {
        when(service.listByArea(AREA)).thenReturn(List.of());

        mvc.perform(get(BASE, AREA))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listReturns404ForUnknownArea() throws Exception {
        when(service.listByArea(AREA)).thenThrow(new EntityNotFoundException("Área", AREA));

        mvc.perform(get(BASE, AREA)).andExpect(status().isNotFound());
    }

    @Test
    void getReturnsPlanOr404WhenItBelongsToAnotherArea() throws Exception {
        UUID planId = UUID.randomUUID();
        when(service.getById(AREA, planId)).thenReturn(output(planId, LocalDate.parse("2026-03-01"), null));
        UUID otherArea = UUID.randomUUID();
        when(service.getById(otherArea, planId)).thenThrow(new EntityNotFoundException("Plano de manejo", planId));

        mvc.perform(get(BASE + "/{planId}", AREA, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()));
        mvc.perform(get(BASE + "/{planId}", otherArea, planId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns400ForMalformedId() throws Exception {
        mvc.perform(get(BASE + "/{planId}", AREA, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("planId"));
    }

    @Test
    void updateReturns200AndMapsFields() throws Exception {
        UUID planId = UUID.randomUUID();
        when(service.update(eq(AREA), eq(planId), any())).thenReturn(
                output(planId, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-08-31")));

        mvc.perform(put(BASE + "/{planId}", AREA, planId).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()));

        ArgumentCaptor<ManagementPlanInput> captor = ArgumentCaptor.forClass(ManagementPlanInput.class);
        verify(service).update(eq(AREA), eq(planId), captor.capture());
        assertThat(captor.getValue().fFactor()).isEqualByComparingTo("0.50");
        assertThat(captor.getValue().sprinklerRateMmPerHour()).isEqualByComparingTo("5.00");
        assertThat(captor.getValue().endDate()).isEqualTo(LocalDate.parse("2026-08-31"));
    }

    @Test
    void updateReturns400Or404Or422() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        UUID locked = UUID.randomUUID();
        when(service.update(eq(AREA), eq(unknown), any())).thenThrow(new EntityNotFoundException("Plano de manejo", unknown));
        when(service.update(eq(AREA), eq(locked), any())).thenThrow(
                new BusinessRuleException("Cultura e solo não podem ser trocados em plano com balanços"));
        when(service.update(eq(AREA), eq(planId), any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("fFactor", "inválido"), new FieldViolation("endDate", "anterior ao início"))));

        mvc.perform(put(BASE + "/{planId}", AREA, unknown).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/{planId}", AREA, locked).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put(BASE + "/{planId}", AREA, planId).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("fFactor", "endDate")));
        mvc.perform(put(BASE + "/{planId}", AREA, planId).contentType(MediaType.APPLICATION_JSON).content("{x"))
                .andExpect(status().isBadRequest());
    }
}
