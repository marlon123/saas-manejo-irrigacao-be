package com.irrigation.presentation;

import com.irrigation.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cenário D do módulo de balanço hídrico (seção 6 do requirements.md): erros, duplicidade, imutabilidade
 * das linhas gravadas e exclusões de registros referenciados, via HTTP sobre o PostgreSQL real.
 *
 * <p>Cobre os Req 1.13, 2.9, 3.5, 4.4, 4.9, 7.7, 8.4, 11 e 12.6. Nas respostas 422 de pré-condição,
 * confere {@code preconditions[]} (código e campos); nos demais, o status e a ausência de efeitos no banco.
 */
class WaterBalanceErrorsApiIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate DAY_2 = DAY_1.plusDays(1);
    private static final LocalDate DAY_3 = DAY_1.plusDays(2);

    @Autowired
    MockMvc mvc;

    /** Identificadores do cadastro mínimo de um teste. */
    private record Setup(UUID areaId, UUID soilId, UUID cropId, UUID planId) {
    }

    // ---------------------------------------------------------------- pré-condições (Req 11)

    @Test
    void missingWeatherReturns422WithWeatherMissingAndPersistsNothing() throws Exception {
        Setup s = setUp("Sem meteorologia", "0.50");
        registerIrrigation(s.areaId(), DAY_1, "0");

        MvcResult result = calculateExpecting(s.areaId(), DAY_1, 422);

        assertThat(codes(result)).containsExactly("WEATHER_MISSING");
        assertThat((String) JsonPath.read(body(result), "$.date")).isEqualTo(DAY_1.toString());
        assertThat(balanceCount(s.areaId())).isZero();
    }

    @Test
    void missingAppliedIrrigationReturns422AndZeroIrrigationCountsAsPresent() throws Exception {
        Setup s = setUp("Sem IR", "0.50");
        registerWeather(s.areaId(), DAY_1, "5", "0");

        MvcResult result = calculateExpecting(s.areaId(), DAY_1, 422);

        assertThat(codes(result)).containsExactly("APPLIED_IRRIGATION_MISSING");
        assertThat(balanceCount(s.areaId())).isZero();

        // IR igual a zero é um valor informado: o cálculo passa
        registerIrrigation(s.areaId(), DAY_1, "0");
        calculateExpecting(s.areaId(), DAY_1, 201);
        assertThat(balanceCount(s.areaId())).isEqualTo(1);
    }

    @Test
    void gapInHistoryReturns422NamingTheFirstDateWithoutBalance() throws Exception {
        Setup s = setUp("Sem histórico", "0.50");
        registerDay(s.areaId(), DAY_3);

        // dia 3 sem os dias 1 e 2 calculados: a primeira data sem cálculo é o dia 1
        MvcResult noHistory = calculateExpecting(s.areaId(), DAY_3, 422);
        assertThat((String) JsonPath.read(body(noHistory), "$.detail")).contains(DAY_1.toString());
        assertThat(balanceCount(s.areaId())).isZero();

        // dia 1 calculado, dia 2 não: a primeira lacuna é o dia 2
        registerDay(s.areaId(), DAY_1);
        calculateExpecting(s.areaId(), DAY_1, 201);
        MvcResult gap = calculateExpecting(s.areaId(), DAY_3, 422);
        assertThat((String) JsonPath.read(body(gap), "$.detail")).contains(DAY_2.toString());
        assertThat(balanceCount(s.areaId())).isEqualTo(1);
    }

    @Test
    void sameDateTwiceReturns422AndKeepsASingleCurrentRow() throws Exception {
        Setup s = setUp("Duplicidade", "0.50");
        registerDay(s.areaId(), DAY_1);
        calculateExpecting(s.areaId(), DAY_1, 201);

        calculateExpecting(s.areaId(), DAY_1, 422);

        assertThat(balanceCount(s.areaId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                + "AND balance_date = ? AND is_current", Integer.class, s.areaId(),
                java.sql.Date.valueOf(DAY_1))).isEqualTo(1);
    }

    @Test
    void soilWithoutGravimetricFieldCapacityReturns422ListingTheMissingFields() throws Exception {
        // solo legado: só o par volumétrico, sem CC e PMP gravimétricos
        UUID soilId = create("/api/v1/soil-profiles", "{\"soilType\":\"Legado\",\"fieldCapacity\":0.30,"
                + "\"permanentWiltingPoint\":0.15,\"bulkDensity\":1.2}");
        UUID cropId = createCrop("Cultura solo legado");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"Solo legado\"}");
        createPlan(areaId, cropId, soilId, "0.50", "6");
        registerDay(areaId, DAY_1);

        MvcResult result = calculateExpecting(areaId, DAY_1, 422);

        assertThat(codes(result)).containsExactly("SOIL_FIELDS_MISSING");
        List<String> fields = JsonPath.read(body(result), "$.preconditions[0].fields");
        assertThat(fields).containsExactlyInAnyOrder("fieldCapacityGravimetric",
                "permanentWiltingPointGravimetric");
        assertThat(balanceCount(areaId)).isZero();
    }

    @Test
    void cropWithoutKcAndRootDepthReturns422WithCropFieldsMissing() throws Exception {
        UUID soilId = createSoil("Solo cultura legada");
        UUID cropId = insertLegacyCrop("Cultura legada");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"Cultura legada\"}");
        createPlan(areaId, cropId, soilId, "0.50", "6");
        registerDay(areaId, DAY_1);

        MvcResult result = calculateExpecting(areaId, DAY_1, 422);

        assertThat(codes(result)).containsExactly("CROP_FIELDS_MISSING");
        List<String> fields = JsonPath.read(body(result), "$.preconditions[0].fields");
        assertThat(fields).containsExactlyInAnyOrder("kc", "rootDepth");
        assertThat(balanceCount(areaId)).isZero();
    }

    @Test
    void allMissingPreconditionsAreAggregatedInASingleResponse() throws Exception {
        UUID soilId = create("/api/v1/soil-profiles", "{\"soilType\":\"Legado\",\"fieldCapacity\":0.30,"
                + "\"permanentWiltingPoint\":0.15,\"bulkDensity\":1.2}");
        UUID cropId = insertLegacyCrop("Cultura legada agregada");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"Agregado\"}");
        createPlan(areaId, cropId, soilId, "0.50", "6");

        MvcResult result = calculateExpecting(areaId, DAY_1, 422);

        assertThat(codes(result)).containsExactly("WEATHER_MISSING", "APPLIED_IRRIGATION_MISSING",
                "SOIL_FIELDS_MISSING", "CROP_FIELDS_MISSING");
        assertThat(balanceCount(areaId)).isZero();
    }

    // ---------------------------------------------------------------- validação e 404

    @Test
    void omittedPrecipitationReturns400WithViolationOnPrecipitationMm() throws Exception {
        Setup s = setUp("P omitido", "0.50");

        mvc.perform(post("/api/v1/areas/" + s.areaId() + "/daily-weather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + DAY_1 + "\",\"etpMm\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field=='precipitationMm')]").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_weather", Integer.class)).isZero();
    }

    @Test
    void zeroSprinklerRateOnPlanCreationReturns400AndCreatesNoPlan() throws Exception {
        UUID soilId = createSoil("Solo taxa zero");
        UUID cropId = createCrop("Cultura taxa zero");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"Taxa zero\"}");

        mvc.perform(post("/api/v1/areas/" + areaId + "/management-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(cropId, soilId, "0.50", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field=='sprinklerRateMmPerHour')]").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM management_plans", Integer.class)).isZero();
    }

    @Test
    void dateBeforeTheFirstPlanReturns400() throws Exception {
        Setup s = setUp("Antes do plano", "0.50");
        LocalDate before = DAY_1.minusDays(1);
        registerDay(s.areaId(), before);

        MvcResult result = calculateExpecting(s.areaId(), before, 400);

        assertThat((List<String>) JsonPath.read(body(result), "$.violations[*].field")).contains("date");
        assertThat(balanceCount(s.areaId())).isZero();
    }

    @Test
    void unknownAreaReturns404() throws Exception {
        calculateExpecting(UUID.randomUUID(), DAY_1, 404);
    }

    // ---------------------------------------------------------------- imutabilidade (Req 12.6)

    @Test
    void changingFactorSoilCropAndWeatherDoesNotChangeStoredRows() throws Exception {
        Setup s = setUp("Imutabilidade", "0.50");
        registerDay(s.areaId(), DAY_1);
        registerDay(s.areaId(), DAY_2);
        calculateExpecting(s.areaId(), DAY_1, 201);
        calculateExpecting(s.areaId(), DAY_2, 201);
        List<Map<String, Object>> before = storedRows(s.areaId());
        assertThat(before).hasSize(2);

        // F e taxa do plano
        mvc.perform(put("/api/v1/areas/" + s.areaId() + "/management-plans/" + s.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planBody(s.cropId(), s.soilId(), "0.80", "9")))
                .andExpect(status().isOk());
        // solo
        mvc.perform(put("/api/v1/soil-profiles/" + s.soilId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Alterado\",\"fieldCapacityGravimetricPct\":40,"
                                + "\"permanentWiltingPointGravimetricPct\":10,\"bulkDensity\":1.5}"))
                .andExpect(status().isOk());
        // cultura
        mvc.perform(put("/api/v1/crops/" + s.cropId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cultura alterada\",\"kc\":1.1,\"rootDepthMm\":700}"))
                .andExpect(status().isOk());
        // meteorologia e IR dos dois dias
        registerWeather(s.areaId(), DAY_1, "9", "7");
        registerWeather(s.areaId(), DAY_2, "9", "7");
        registerIrrigation(s.areaId(), DAY_1, "20");
        registerIrrigation(s.areaId(), DAY_2, "20");

        assertThat(storedRows(s.areaId())).isEqualTo(before);
    }

    // ---------------------------------------------------------------- exclusões (Req 1.13, 2.9, 3.5)

    @Test
    void deletingReferencedAreaSoilAndCropReturns422WithoutChangingRecords() throws Exception {
        Setup s = setUp("Exclusões", "0.50");
        registerDay(s.areaId(), DAY_1);
        calculateExpecting(s.areaId(), DAY_1, 201);
        Map<String, Integer> before = counts();

        mvc.perform(delete("/api/v1/areas/" + s.areaId())).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/v1/soil-profiles/" + s.soilId())).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/v1/crops/" + s.cropId())).andExpect(status().isUnprocessableEntity());

        assertThat(counts()).isEqualTo(before);
        assertThat(before.get("areas")).isEqualTo(1);
        assertThat(before.get("soil_profiles")).isEqualTo(1);
        assertThat(before.get("crops")).isEqualTo(1);
        assertThat(before.get("management_plans")).isEqualTo(1);
        assertThat(before.get("daily_water_balance")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- auxiliares

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static List<String> codes(MvcResult result) throws Exception {
        return JsonPath.read(body(result), "$.preconditions[*].code");
    }

    private int balanceCount(UUID areaId) {
        return jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ?",
                Integer.class, areaId);
    }

    private List<Map<String, Object>> storedRows(UUID areaId) {
        return jdbc.queryForList("SELECT * FROM daily_water_balance WHERE area_id = ? "
                + "ORDER BY balance_date, revision", areaId);
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (String table : List.of("areas", "soil_profiles", "crops", "management_plans", "daily_weather",
                "applied_irrigation", "daily_water_balance")) {
            result.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
        }
        return result;
    }

    private MvcResult calculateExpecting(UUID areaId, LocalDate date, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/v1/areas/" + areaId + "/water-balance/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"cycleDay\":1}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    /** Cadastro completo (solo, cultura, área) e plano iniciando no dia 1. */
    private Setup setUp(String name, String fFactor) throws Exception {
        UUID soilId = createSoil("Solo " + name);
        UUID cropId = createCrop("Cultura " + name);
        UUID areaId = create("/api/v1/areas", "{\"name\":\"" + name + "\"}");
        UUID planId = createPlan(areaId, cropId, soilId, fFactor, "6");
        return new Setup(areaId, soilId, cropId, planId);
    }

    private UUID createSoil(String type) throws Exception {
        return create("/api/v1/soil-profiles", "{\"soilType\":\"" + type + "\","
                + "\"fieldCapacityGravimetricPct\":30,\"permanentWiltingPointGravimetricPct\":15,"
                + "\"bulkDensity\":1.2}");
    }

    private UUID createCrop(String name) throws Exception {
        return create("/api/v1/crops", "{\"name\":\"" + name + "\",\"kc\":0.8,\"rootDepthMm\":400}");
    }

    /** Cultura anterior ao módulo, sem KC e sem Z: só é possível por inserção direta. */
    private UUID insertLegacyCrop(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO crops (id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID createPlan(UUID areaId, UUID cropId, UUID soilId, String fFactor, String rate)
            throws Exception {
        return create("/api/v1/areas/" + areaId + "/management-plans",
                planBody(cropId, soilId, fFactor, rate));
    }

    private static String planBody(UUID cropId, UUID soilId, String fFactor, String rate) {
        return "{\"cropId\":\"" + cropId + "\",\"soilProfileId\":\"" + soilId + "\",\"startDate\":\""
                + DAY_1 + "\",\"fFactor\":" + fFactor + ",\"sprinklerRateMmPerHour\":" + rate + "}";
    }

    /** Meteorologia e IR (zero) do dia. */
    private void registerDay(UUID areaId, LocalDate date) throws Exception {
        registerWeather(areaId, date, "5", "0");
        registerIrrigation(areaId, date, "0");
    }

    private void registerWeather(UUID areaId, LocalDate date, String etp, String precipitation)
            throws Exception {
        mvc.perform(post("/api/v1/areas/" + areaId + "/daily-weather").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"etpMm\":" + etp
                                + ",\"precipitationMm\":" + precipitation + "}"))
                .andExpect(status().isOk());
    }

    private void registerIrrigation(UUID areaId, LocalDate date, String mm) throws Exception {
        mvc.perform(post("/api/v1/areas/" + areaId + "/applied-irrigation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"appliedIrrigationMm\":" + mm + "}"))
                .andExpect(status().isOk());
    }

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
