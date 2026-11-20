package com.irrigation.presentation.controller;

import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.service.WaterBalanceQueryService;
import com.irrigation.application.service.WaterBalanceService;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.exception.PreconditionsNotMetException.Precondition;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.waterbalance.NoRecommendationReason;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import com.irrigation.presentation.mapper.WaterBalanceDtoMapperImpl;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes {@code MockMvc} do {@link WaterBalanceController}. Os serviços de cálculo e reprocessamento são
 * simulados; o serviço de consulta é o real (com repositórios simulados), para que a validação de
 * paginação e a política de arredondamento sejam exercitadas de ponta a ponta.
 *
 * <p>Requisitos: 7.3, 10.3, 11.5, 13.8, 14.1, 14.4, 14.5, 14.6, 14.8, 15.7.
 */
@WebMvcTest(WaterBalanceController.class)
@Import({WaterBalanceDtoMapperImpl.class, WaterBalanceQueryService.class})
class WaterBalanceControllerTest {

    private static final String PROBLEM = "application/problem+json";
    private static final UUID AREA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DAY = LocalDate.of(2026, 4, 10);
    private static final String BASE = "/api/v1/areas/" + AREA_ID + "/water-balance";

    @Autowired
    MockMvc mvc;

    @MockBean
    WaterBalanceService service;

    @MockBean
    DailyWaterBalanceRepository balances;

    @MockBean
    AreaRepository areas;

    // ------------------------------------------------------------------ fixtures

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /**
     * Balanço com valores de escala longa, para exercitar a política de arredondamento: I (3,456) e IR (2,755)
     * distintos; tempo de 0,9933333333 h = 59,6 min.
     */
    private static DailyWaterBalance balance(LocalDate date, int revision, boolean current, String operatingHours,
                                             boolean recommended, NoRecommendationReason reason) {
        WaterBalanceInputs inputs = new WaterBalanceInputs(
                new EtpDepth(bd("5.2")), new PrecipitationDepth(bd("1.1")),
                new AppliedIrrigationDepth(bd("2.7")),
                new GravimetricWaterContent(bd("30")), new GravimetricWaterContent(bd("15")),
                new BulkDensity(bd("1.2")), new RootDepth(bd("400")), new Kc(bd("0.8")),
                new AvailabilityFactor(bd("0.5")), new SprinklerRate(bd("6")), reason != null, reason != null ? null : bd("36"));
        WaterBalanceResult result = new WaterBalanceResult(
                bd("4.005"), bd("72.0049"), bd("36.1234"), bd("32.6674"), bd("3.456"),
                recommended, reason, bd(operatingHours));
        return new DailyWaterBalance(UUID.randomUUID(), AREA_ID, date, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new CycleDay(3), inputs, result, TestData.fixedClock().instant(),
                "water-balance", "1.0", revision, current);
    }

    private static DailyWaterBalance balance(LocalDate date, int revision, boolean current) {
        return balance(date, revision, current, "0.9933333333", true, null);
    }

    private static WaterBalanceOutput output(LocalDate date, int revision, boolean current) {
        return WaterBalanceOutput.from(balance(date, revision, current));
    }

    private static String calculateBody() {
        return "{\"date\":\"2026-04-10\",\"cycleDay\":3}";
    }

    /** Verifica a política de arredondamento do Req 15 sobre o objeto de balanço em {@code prefix}. */
    private static void assertRounding(ResultActions result, String prefix) throws Exception {
        result.andExpect(jsonPath(prefix + ".inputs.etpMm").value(5.2))
                .andExpect(jsonPath(prefix + ".inputs.precipitationMm").value(1.1))
                .andExpect(jsonPath(prefix + ".inputs.appliedIrrigationMm").value(2.7))
                .andExpect(jsonPath(prefix + ".inputs.rootDepthMm").value(400.0))
                .andExpect(jsonPath(prefix + ".results.etcMm").value(4.01))
                .andExpect(jsonPath(prefix + ".results.cadMm").value(72.00))
                .andExpect(jsonPath(prefix + ".results.afdInitialMm").value(36.12))
                .andExpect(jsonPath(prefix + ".results.afdFinalMm").value(32.67))
                .andExpect(jsonPath(prefix + ".results.idealIrrigationMm").value(3.46))
                .andExpect(jsonPath(prefix + ".results.operatingTime.hours").value(0.9933))
                // 59,6 min arredonda para 60 min: vira 1 h 00 min, nunca 0 h 60 min
                .andExpect(jsonPath(prefix + ".results.operatingTime.wholeHours").value(1))
                .andExpect(jsonPath(prefix + ".results.operatingTime.minutes").value(0));
    }

    // ------------------------------------------------------------------ POST /calculate

    @Test
    void calculateReturns201WithLocationAndThreeGroups() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(output(DAY, 1, true));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(BASE + "/2026-04-10")))
                .andExpect(jsonPath("$.areaId").value(AREA_ID.toString()))
                .andExpect(jsonPath("$.date").value("2026-04-10"))
                .andExpect(jsonPath("$.cycleDay").value(3))
                .andExpect(jsonPath("$.inputs").exists())
                .andExpect(jsonPath("$.results").exists())
                .andExpect(jsonPath("$.control.methodologyId").value("water-balance"))
                .andExpect(jsonPath("$.control.methodologyVersion").value("1.0"))
                .andExpect(jsonPath("$.control.revision").value(1))
                .andExpect(jsonPath("$.control.current").value(true))
                .andExpect(jsonPath("$.control.calculatedAt").exists());
    }

    @Test
    void calculateKeepsIdealIrrigationAndAppliedIrrigationDistinct() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(output(DAY, 1, true));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(jsonPath("$.results.idealIrrigationMm").value(3.46))
                .andExpect(jsonPath("$.inputs.appliedIrrigationMm").value(2.7))
                .andExpect(jsonPath("$.results.appliedIrrigationMm").doesNotExist())
                .andExpect(jsonPath("$.inputs.idealIrrigationMm").doesNotExist());
    }

    @Test
    void calculateReturnsIrrigationRecommendedTrueWithNullReason() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(output(DAY, 1, true));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(jsonPath("$.results.irrigationRecommended").value(true))
                .andExpect(jsonPath("$.results.noRecommendationReason").value(nullValue()));
    }

    @Test
    void calculateReturnsNoRecommendationReasonOnTheFirstDay() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(WaterBalanceOutput.from(
                balance(DAY, 1, true, "0", false, NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE)));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results.irrigationRecommended").value(false))
                .andExpect(jsonPath("$.results.noRecommendationReason").value("FIRST_DAY_OF_FIRST_CYCLE"))
                .andExpect(jsonPath("$.results.operatingTime.hours").value(0.0))
                .andExpect(jsonPath("$.results.operatingTime.wholeHours").value(0))
                .andExpect(jsonPath("$.results.operatingTime.minutes").value(0));
    }

    @Test
    void calculatePassesTheRequestToTheService() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(output(DAY, 1, true));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isCreated());

        verify(service).calculate(eq(AREA_ID),
                eq(new com.irrigation.application.dto.CalculateWaterBalanceInput(DAY, 3)));
    }

    @Test
    void calculateReturns400WithAllViolations() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenThrow(new ValidationFailedException(List.of(
                new FieldViolation("date", "é obrigatório"),
                new FieldViolation("cycleDay", "é obrigatório"))));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[*].field", hasItems("date", "cycleDay")));
    }

    @Test
    void calculateReturns400ForAnInvalidDateFormat() throws Exception {
        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"10/04/2026\",\"cycleDay\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("date"));

        verifyNoInteractions(service);
    }

    @Test
    void calculateReturns400ForAMalformedBody() throws Exception {
        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void calculateReturns404ForAnUnknownArea() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenThrow(new EntityNotFoundException("Área", AREA_ID));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void calculateReturns409WhenAProcessingIsInProgress() throws Exception {
        when(service.calculate(eq(AREA_ID), any()))
                .thenThrow(new ConflictException("Já existe um processamento em andamento para a área"));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value(containsString("processamento")));
    }

    @Test
    void calculateReturns422WithOneEntryPerFailedPrecondition() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenThrow(new PreconditionsNotMetException(DAY, List.of(
                new Precondition(Code.WEATHER_MISSING, "Meteorologia ausente", List.of("etp", "precipitation")),
                new Precondition(Code.APPLIED_IRRIGATION_MISSING, "Irrigação real ausente"),
                new Precondition(Code.SOIL_FIELDS_MISSING, "Solo incompleto", List.of("fieldCapacity")),
                new Precondition(Code.CROP_FIELDS_MISSING, "Cultura incompleta", List.of("rootDepth")))));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.date").value("2026-04-10"))
                .andExpect(jsonPath("$.preconditions", hasSize(4)))
                .andExpect(jsonPath("$.preconditions[0].code").value("WEATHER_MISSING"))
                .andExpect(jsonPath("$.preconditions[0].message").value("Meteorologia ausente"))
                .andExpect(jsonPath("$.preconditions[0].fields", hasItems("etp", "precipitation")))
                .andExpect(jsonPath("$.preconditions[1].code").value("APPLIED_IRRIGATION_MISSING"))
                .andExpect(jsonPath("$.preconditions[1].fields", hasSize(0)))
                .andExpect(jsonPath("$.preconditions[2].code").value("SOIL_FIELDS_MISSING"))
                .andExpect(jsonPath("$.preconditions[2].fields[0]").value("fieldCapacity"))
                .andExpect(jsonPath("$.preconditions[3].code").value("CROP_FIELDS_MISSING"))
                .andExpect(jsonPath("$.preconditions[3].fields[0]").value("rootDepth"));
    }

    @Test
    void calculateReturns422WithASinglePrecondition() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenThrow(new PreconditionsNotMetException(DAY, List.of(
                new Precondition(Code.WEATHER_MISSING, "Meteorologia ausente", List.of("etp")))));

        mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON).content(calculateBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.preconditions", hasSize(1)))
                .andExpect(jsonPath("$.preconditions[0].code").value("WEATHER_MISSING"));
    }

    // ------------------------------------------------------------------ POST /reprocess

    @Test
    void reprocessReturns200WithTheListOfNewRevisions() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenReturn(List.of(
                output(DAY, 2, true), output(DAY.plusDays(1), 2, true)));

        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-04-10"))
                .andExpect(jsonPath("$[0].control.revision").value(2))
                .andExpect(jsonPath("$[1].date").value("2026-04-11"))
                .andExpect(jsonPath("$[1].control.current").value(true));
    }

    @Test
    void reprocessReturns400ForAMissingDate() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenThrow(
                new ValidationFailedException(List.of(new FieldViolation("date", "é obrigatório"))));

        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("date"));
    }

    @Test
    void reprocessReturns400ForAnInvalidDateFormat() throws Exception {
        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-13-45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("date"));
    }

    @Test
    void reprocessReturns404ForAnUnknownArea() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenThrow(new EntityNotFoundException("Área", AREA_ID));

        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reprocessReturns409WhenAProcessingIsInProgress() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenThrow(new ConflictException("Processamento em andamento"));

        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void reprocessReturns422WithPreconditionsAndTheFailingDate() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenThrow(new PreconditionsNotMetException(
                DAY.plusDays(2), List.of(
                new Precondition(Code.WEATHER_MISSING, "Meteorologia ausente", List.of("etp")),
                new Precondition(Code.APPLIED_IRRIGATION_MISSING, "Irrigação real ausente"))));

        mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-04-10\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.date").value("2026-04-12"))
                .andExpect(jsonPath("$.preconditions", hasSize(2)));
    }

    // ------------------------------------------------------------------ GET list

    @Test
    void listReturns200PagedWithTheDefaultPagination() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrentByPeriod(eq(AREA_ID), any(), any(), any())).thenReturn(List.of(
                balance(DAY, 1, true), balance(DAY.plusDays(1), 1, true)));
        when(balances.countCurrentByPeriod(eq(AREA_ID), any(), any())).thenReturn(2L);

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(balances).findCurrentByPeriod(AREA_ID, null, null, new PageRequest(0, 20));
    }

    @Test
    void listPassesPeriodAndPaginationToTheRepository() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrentByPeriod(eq(AREA_ID), any(), any(), any())).thenReturn(List.of(balance(DAY, 1, true)));
        when(balances.countCurrentByPeriod(AREA_ID, DAY, DAY.plusDays(5))).thenReturn(7L);

        mvc.perform(get(BASE).param("from", "2026-04-10").param("to", "2026-04-15")
                        .param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(balances).findCurrentByPeriod(AREA_ID, DAY, DAY.plusDays(5), new PageRequest(1, 3));
    }

    @Test
    void listReturnsAnEmptyPageWhenNothingMatches() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrentByPeriod(eq(AREA_ID), any(), any(), any())).thenReturn(List.of());
        when(balances.countCurrentByPeriod(eq(AREA_ID), any(), any())).thenReturn(0L);

        mvc.perform(get(BASE).param("from", "2030-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listReturns400ForANegativePageWithoutAdjusting() throws Exception {
        mvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field", hasItems("page")));

        verify(balances, never()).findCurrentByPeriod(any(), any(), any(), any());
    }

    @Test
    void listReturns400ForSizeZeroWithoutAdjusting() throws Exception {
        mvc.perform(get(BASE).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("size")));

        verify(balances, never()).findCurrentByPeriod(any(), any(), any(), any());
    }

    @Test
    void listReturns400ForSize101WithoutAdjusting() throws Exception {
        mvc.perform(get(BASE).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("size")));

        verify(balances, never()).findCurrentByPeriod(any(), any(), any(), any());
    }

    @Test
    void listAcceptsTheMaximumSizeOf100() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrentByPeriod(eq(AREA_ID), any(), any(), any())).thenReturn(List.of());

        mvc.perform(get(BASE).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listAccumulatesPaginationAndPeriodViolations() throws Exception {
        mvc.perform(get(BASE).param("from", "2026-05-01").param("to", "2026-04-01")
                        .param("page", "-2").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[*].field", hasItems("from", "page", "size")));
    }

    @Test
    void listReturns400ForAnInvalidDateFormat() throws Exception {
        mvc.perform(get(BASE).param("from", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("from"));
    }

    @Test
    void listReturns400ForANonNumericPage() throws Exception {
        mvc.perform(get(BASE).param("page", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("page"));
    }

    @Test
    void listReturns404ForAnUnknownArea() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(false);

        mvc.perform(get(BASE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ------------------------------------------------------------------ GET /{date}

    @Test
    void getReturns200WithTheCurrentBalance() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrent(AREA_ID, DAY)).thenReturn(java.util.Optional.of(balance(DAY, 1, true)));

        mvc.perform(get(BASE + "/2026-04-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-04-10"))
                .andExpect(jsonPath("$.control.current").value(true))
                .andExpect(jsonPath("$.results.irrigationRecommended").value(true))
                .andExpect(jsonPath("$.results.noRecommendationReason").value(nullValue()));
    }

    @Test
    void getReturns404WhenTheDateWasNotCalculated() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrent(AREA_ID, DAY)).thenReturn(java.util.Optional.empty());

        mvc.perform(get(BASE + "/2026-04-10"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns404ForAnUnknownArea() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(false);

        mvc.perform(get(BASE + "/2026-04-10"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReturns400ForAnInvalidDateFormat() throws Exception {
        mvc.perform(get(BASE + "/10-04-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[0].field").value("date"));

        verifyNoInteractions(balances);
    }

    // ------------------------------------------------------------------ GET /{date}/revisions

    @Test
    void revisionsReturnsAllRevisionsFromNewestToOldest() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findRevisions(AREA_ID, DAY)).thenReturn(List.of(
                balance(DAY, 3, true), balance(DAY, 2, false), balance(DAY, 1, false)));

        mvc.perform(get(BASE + "/2026-04-10/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].control.revision").value(3))
                .andExpect(jsonPath("$[0].control.current").value(true))
                .andExpect(jsonPath("$[1].control.revision").value(2))
                .andExpect(jsonPath("$[1].control.current").value(false))
                .andExpect(jsonPath("$[2].control.revision").value(1));
    }

    @Test
    void revisionsReturns404WhenTheDateWasNeverCalculated() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findRevisions(AREA_ID, DAY)).thenReturn(List.of());

        mvc.perform(get(BASE + "/2026-04-10/revisions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revisionsReturns400ForAnInvalidDateFormat() throws Exception {
        mvc.perform(get(BASE + "/xyz/revisions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("date"));
    }

    // ------------------------------------------------------------------ política de arredondamento

    @Test
    void roundingPolicyIsTheSameInCalculate() throws Exception {
        when(service.calculate(eq(AREA_ID), any())).thenReturn(output(DAY, 1, true));

        assertRounding(mvc.perform(post(BASE + "/calculate").contentType(MediaType.APPLICATION_JSON)
                .content(calculateBody())), "$");
    }

    @Test
    void roundingPolicyIsTheSameInReprocess() throws Exception {
        when(service.reprocess(eq(AREA_ID), any())).thenReturn(List.of(output(DAY, 2, true)));

        assertRounding(mvc.perform(post(BASE + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-04-10\"}")), "$[0]");
    }

    @Test
    void roundingPolicyIsTheSameInList() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrentByPeriod(eq(AREA_ID), any(), any(), any())).thenReturn(List.of(balance(DAY, 1, true)));
        when(balances.countCurrentByPeriod(eq(AREA_ID), any(), any())).thenReturn(1L);

        assertRounding(mvc.perform(get(BASE)), "$.content[0]");
    }

    @Test
    void roundingPolicyIsTheSameInGetByDate() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findCurrent(AREA_ID, DAY)).thenReturn(java.util.Optional.of(balance(DAY, 1, true)));

        assertRounding(mvc.perform(get(BASE + "/2026-04-10")), "$");
    }

    @Test
    void roundingPolicyIsTheSameInRevisions() throws Exception {
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findRevisions(AREA_ID, DAY)).thenReturn(List.of(balance(DAY, 1, true)));

        assertRounding(mvc.perform(get(BASE + "/2026-04-10/revisions")), "$[0]");
    }

    @Test
    void operatingTimeMinutesStayBetween0And59() throws Exception {
        // 2,5 h = 2 h 30 min; 0,0084 h = 0,504 min, arredonda para 1 min
        when(areas.existsById(AREA_ID)).thenReturn(true);
        when(balances.findRevisions(AREA_ID, DAY)).thenReturn(List.of(
                balance(DAY, 2, true, "2.5000000000", true, null),
                balance(DAY, 1, false, "0.0084000000", true, null)));

        mvc.perform(get(BASE + "/2026-04-10/revisions"))
                .andExpect(jsonPath("$[0].results.operatingTime.hours").value(2.5))
                .andExpect(jsonPath("$[0].results.operatingTime.wholeHours").value(2))
                .andExpect(jsonPath("$[0].results.operatingTime.minutes").value(30))
                .andExpect(jsonPath("$[1].results.operatingTime.hours").value(0.0084))
                .andExpect(jsonPath("$[1].results.operatingTime.wholeHours").value(0))
                .andExpect(jsonPath("$[1].results.operatingTime.minutes").value(1));
    }
}
