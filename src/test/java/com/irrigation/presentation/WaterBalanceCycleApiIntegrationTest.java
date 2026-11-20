package com.irrigation.presentation;

import com.irrigation.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida HTTP dos Cenários A e B do módulo de balanço hídrico (seção 6 do requirements.md),
 * sobre o PostgreSQL real e o contexto completo do Spring.
 *
 * <p>Cada linha da tabela é conferida duas vezes: na resposta, arredondada (mm com 2 casas e horas com
 * 4 casas), e na tabela {@code daily_water_balance}, sem arredondamento (comparação por
 * {@code compareTo}). Cobre os Req 7, 8, 9, 10, 14 e 15.
 *
 * <p>As datas são fixas e passadas em relação ao relógio do contexto.
 */
class WaterBalanceCycleApiIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 1);

    @Autowired
    MockMvc mvc;

    /** Uma linha da tabela do cenário: entradas e valores esperados. */
    private record Row(int day, String etp, String p, String ir, String etc, String afdi, String afdf,
                       String ideal, boolean recommended, String reason, String hoursPersisted,
                       String hoursShown, int wholeHours, int minutes) {
    }

    // Cenário A (F = 0,50). Dias 1 a 7, TR 1, 2, 3, 1, 2, 3, 1.
    private static final List<Row> SCENARIO_A = List.of(
            new Row(1, "5.00", "0", "0", "4", "36", "32", "4", false, "FIRST_DAY_OF_FIRST_CYCLE", "0", "0.0000", 0, 0),
            new Row(2, "6.25", "0", "0", "5", "32", "27", "5", true, null, "0.8333333333", "0.8333", 0, 50),
            new Row(3, "7.50", "2", "5", "6", "27", "28", "-1", false, null, "0", "0.0000", 0, 0),
            new Row(4, "6.25", "0", "0", "5", "28", "23", "5", true, null, "0.8333333333", "0.8333", 0, 50),
            new Row(5, "10.00", "0", "12", "8", "23", "27", "-4", false, null, "0", "0.0000", 0, 0),
            new Row(6, "5.00", "0", "0", "4", "27", "23", "4", true, null, "0.6666666667", "0.6667", 0, 40),
            new Row(7, "15.00", "0", "0", "12", "23", "11", "12", true, null, "2", "2.0000", 2, 0));

    private static final int[] SCENARIO_A_CYCLE_DAYS = {1, 2, 3, 1, 2, 3, 1};

    // ---------------------------------------------------------------- Cenário A

    @Test
    void scenarioAEvolvesSevenDaysMatchingTheTableAndPersistingUnrounded() throws Exception {
        UUID areaId = setUpArea("Cenário A", "0.50");
        for (Row row : SCENARIO_A) {
            registerDay(areaId, row);
        }

        for (Row row : SCENARIO_A) {
            LocalDate date = DAY_1.plusDays(row.day() - 1);
            MvcResult result = mvc.perform(post("/api/v1/areas/" + areaId + "/water-balance/calculate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + date + "\",\"cycleDay\":"
                                    + SCENARIO_A_CYCLE_DAYS[row.day() - 1] + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.date").value(date.toString()))
                    .andReturn();
            assertCalculatedDay(result.getResponse().getContentAsString(), row);
        }

        // valores persistidos, sem arredondamento
        for (Row row : SCENARIO_A) {
            Map<String, Object> db = persisted(areaId, DAY_1.plusDays(row.day() - 1));
            assertThat((BigDecimal) db.get("cad_mm")).isEqualByComparingTo("72");
            assertThat((BigDecimal) db.get("etc_mm")).isEqualByComparingTo(row.etc());
            assertThat((BigDecimal) db.get("afd_initial_mm")).isEqualByComparingTo(row.afdi());
            assertThat((BigDecimal) db.get("afd_final_mm")).isEqualByComparingTo(row.afdf());
            assertThat((BigDecimal) db.get("ideal_irrigation_mm")).isEqualByComparingTo(row.ideal());
            assertThat(db.get("irrigation_recommended")).isEqualTo(row.recommended());
            assertThat(db.get("no_recommendation_reason")).isEqualTo(row.reason());
            assertThat((BigDecimal) db.get("operating_time_hours")).isEqualByComparingTo(row.hoursPersisted());
            assertThat(db.get("cycle_day_tr")).isEqualTo(SCENARIO_A_CYCLE_DAYS[row.day() - 1]);
            assertThat(db.get("revision")).isEqualTo(1);
            assertThat(db.get("is_current")).isEqualTo(true);
            assertThat((BigDecimal) db.get("field_capacity_pct")).isEqualByComparingTo("30");
            assertThat((BigDecimal) db.get("wilting_point_pct")).isEqualByComparingTo("15");
            assertThat((BigDecimal) db.get("bulk_density_g_cm3")).isEqualByComparingTo("1.2");
            assertThat((BigDecimal) db.get("root_depth_mm")).isEqualByComparingTo("400");
            assertThat((BigDecimal) db.get("kc")).isEqualByComparingTo("0.8");
            assertThat((BigDecimal) db.get("f_factor")).isEqualByComparingTo("0.5");
            assertThat((BigDecimal) db.get("sprinkler_rate_mm_h")).isEqualByComparingTo("6");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ?",
                Integer.class, areaId)).isEqualTo(7);
    }

    @Test
    void scenarioAQueriesByPeriodWithPaginationAndByDate() throws Exception {
        UUID areaId = setUpArea("Cenário A consultas", "0.50");
        for (Row row : SCENARIO_A) {
            registerDay(areaId, row);
            calculate(areaId, DAY_1.plusDays(row.day() - 1), SCENARIO_A_CYCLE_DAYS[row.day() - 1]);
        }
        String base = "/api/v1/areas/" + areaId + "/water-balance";

        // todos os dias, em uma página
        mvc.perform(get(base))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content.length()").value(7))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.content[6].date").value("2026-11-07"));

        // período inclusivo de 2026-11-02 a 2026-11-04
        mvc.perform(get(base).param("from", "2026-11-02").param("to", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.content[2].date").value("2026-11-04"));

        // paginação: página 1 de tamanho 3 traz os dias 4 a 6
        mvc.perform(get(base).param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].date").value("2026-11-04"))
                .andExpect(jsonPath("$.content[2].date").value("2026-11-06"));

        // por data: confere a linha do dia 7 (12 mm ÷ 6 mm/h = 2 h)
        Row day7 = SCENARIO_A.get(6);
        MvcResult byDate = mvc.perform(get(base + "/2026-11-07")).andExpect(status().isOk()).andReturn();
        assertCalculatedDay(byDate.getResponse().getContentAsString(), day7);

        // data sem balanço: 404
        mvc.perform(get(base + "/2026-11-08")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- Cenário B

    @Test
    void scenarioBKeepsAfdFinalAboveCadAndUsesItTheNextDay() throws Exception {
        UUID areaId = setUpArea("Cenário B", "0.90");
        Row day1 = new Row(1, "5.00", "20", "0", "4", "64.8", "80.8", "-16", false,
                "FIRST_DAY_OF_FIRST_CYCLE", "0", "0.0000", 0, 0);
        Row day2 = new Row(2, "5.00", "0", "0", "4", "80.8", "76.8", "4", true,
                null, "0.6666666667", "0.6667", 0, 40);
        registerDay(areaId, day1);
        registerDay(areaId, day2);

        MvcResult r1 = calculate(areaId, DAY_1, 1);
        assertCalculatedDay(r1.getResponse().getContentAsString(), day1);
        MvcResult r2 = calculate(areaId, DAY_1.plusDays(1), 2);
        assertCalculatedDay(r2.getResponse().getContentAsString(), day2);

        // AFDf do dia 1 (80,80) fica acima do CAD (72) e é mantida, sem truncar
        Map<String, Object> db1 = persisted(areaId, DAY_1);
        assertThat((BigDecimal) db1.get("cad_mm")).isEqualByComparingTo("72");
        assertThat((BigDecimal) db1.get("afd_initial_mm")).isEqualByComparingTo("64.8");
        assertThat((BigDecimal) db1.get("afd_final_mm")).isEqualByComparingTo("80.8");
        assertThat((BigDecimal) db1.get("afd_final_mm")).isGreaterThan((BigDecimal) db1.get("cad_mm"));
        assertThat((BigDecimal) db1.get("ideal_irrigation_mm")).isEqualByComparingTo("-16");

        Map<String, Object> db2 = persisted(areaId, DAY_1.plusDays(1));
        assertThat((BigDecimal) db2.get("afd_initial_mm")).isEqualByComparingTo("80.8");
        assertThat((BigDecimal) db2.get("afd_final_mm")).isEqualByComparingTo("76.8");
        assertThat((BigDecimal) db2.get("ideal_irrigation_mm")).isEqualByComparingTo("4");
        assertThat((BigDecimal) db2.get("operating_time_hours")).isEqualByComparingTo("0.6666666667");
        assertThat(db2.get("irrigation_recommended")).isEqualTo(true);
        assertThat((BigDecimal) db2.get("f_factor")).isEqualByComparingTo("0.9");
    }

    // ---------------------------------------------------------------- auxiliares

    /** Confere a resposta de um dia: valores arredondados (mm com 2 casas, horas com 4). */
    private static void assertCalculatedDay(String json, Row row) {
        assertThat(dec(json, "$.results.cadMm")).isEqualByComparingTo("72");
        assertThat(dec(json, "$.results.etcMm")).isEqualByComparingTo(row.etc());
        assertThat(dec(json, "$.results.afdInitialMm")).isEqualByComparingTo(row.afdi());
        assertThat(dec(json, "$.results.afdFinalMm")).isEqualByComparingTo(row.afdf());
        assertThat(dec(json, "$.results.idealIrrigationMm")).isEqualByComparingTo(row.ideal());
        assertThat((Boolean) JsonPath.read(json, "$.results.irrigationRecommended")).isEqualTo(row.recommended());
        assertThat((Object) JsonPath.read(json, "$.results.noRecommendationReason")).isEqualTo(row.reason());
        assertThat(dec(json, "$.results.operatingTime.hours")).isEqualByComparingTo(row.hoursShown());
        assertThat((Integer) JsonPath.read(json, "$.results.operatingTime.wholeHours")).isEqualTo(row.wholeHours());
        assertThat((Integer) JsonPath.read(json, "$.results.operatingTime.minutes")).isEqualTo(row.minutes());
        assertThat((Integer) JsonPath.read(json, "$.control.revision")).isEqualTo(1);
        assertThat((Boolean) JsonPath.read(json, "$.control.current")).isTrue();
        assertThat((Object) JsonPath.read(json, "$.control.methodologyId")).isNotNull();

        // escalas de apresentação no texto da resposta: 2 casas em mm e 4 em horas
        assertThat(json).contains("\"afdInitialMm\":" + scaled(row.afdi(), 2));
        assertThat(json).contains("\"afdFinalMm\":" + scaled(row.afdf(), 2));
        assertThat(json).contains("\"idealIrrigationMm\":" + scaled(row.ideal(), 2));
        assertThat(json).contains("\"etcMm\":" + scaled(row.etc(), 2));
        assertThat(json).contains("\"hours\":" + row.hoursShown());
    }

    private static String scaled(String value, int scale) {
        return new BigDecimal(value).setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal dec(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    private Map<String, Object> persisted(UUID areaId, LocalDate date) {
        return jdbc.queryForMap("SELECT * FROM daily_water_balance WHERE area_id = ? AND balance_date = ? "
                + "AND is_current", areaId, Date.valueOf(date));
    }

    private MvcResult calculate(UUID areaId, LocalDate date, int cycleDay) throws Exception {
        return mvc.perform(post("/api/v1/areas/" + areaId + "/water-balance/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"cycleDay\":" + cycleDay + "}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /** Cadastro mínimo (solo, cultura, área) e plano iniciando no dia 1. Retorna o id da área. */
    private UUID setUpArea(String areaName, String fFactor) throws Exception {
        UUID soilId = create("/api/v1/soil-profiles", "{\"soilType\":\"Cenário\","
                + "\"fieldCapacityGravimetricPct\":30,\"permanentWiltingPointGravimetricPct\":15,"
                + "\"bulkDensity\":1.2}");
        UUID cropId = create("/api/v1/crops", "{\"name\":\"Cultura " + areaName + "\",\"kc\":0.8,"
                + "\"rootDepthMm\":400}");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"" + areaName + "\"}");
        create("/api/v1/areas/" + areaId + "/management-plans", "{\"cropId\":\"" + cropId + "\","
                + "\"soilProfileId\":\"" + soilId + "\",\"startDate\":\"" + DAY_1 + "\","
                + "\"fFactor\":" + fFactor + ",\"sprinklerRateMmPerHour\":6}");
        return areaId;
    }

    /** Registra meteorologia e Irrigação Real do dia (data no corpo). */
    private void registerDay(UUID areaId, Row row) throws Exception {
        LocalDate date = DAY_1.plusDays(row.day() - 1);
        mvc.perform(post("/api/v1/areas/" + areaId + "/daily-weather").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"etpMm\":" + row.etp()
                                + ",\"precipitationMm\":" + row.p() + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/areas/" + areaId + "/applied-irrigation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"appliedIrrigationMm\":" + row.ir() + "}"))
                .andExpect(status().isOk());
    }

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
