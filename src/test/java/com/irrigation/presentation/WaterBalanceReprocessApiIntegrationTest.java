package com.irrigation.presentation;

import com.irrigation.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reprocessamento (Cenário E, Req 13) e concorrência por HTTP, sobre o PostgreSQL real e o contexto
 * completo do Spring.
 *
 * <p>Parte do Cenário A calculado (7 dias, F = 0,50), corrige a ETP do dia 2 de 6,25 para 7,50 e
 * reprocessa a partir do dia 2. Confere revisões preservadas, uma corrente por dia, falhas tudo-ou-nada
 * e o bloqueio por área (409).
 */
@Tag("integration")
class WaterBalanceReprocessApiIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 1);

    // Cenário A: ETP, P e IR dos dias 1 a 7, e o TR de cada dia.
    private static final String[] ETP = {"5.00", "6.25", "7.50", "6.25", "10.00", "5.00", "15.00"};
    private static final String[] P = {"0", "0", "2", "0", "0", "0", "0"};
    private static final String[] IR = {"0", "0", "5", "0", "12", "0", "0"};
    private static final int[] CYCLE_DAY = {1, 2, 3, 1, 2, 3, 1};

    @Autowired
    MockMvc mvc;

    // ---------------------------------------------------------------- Cenário E

    @Test
    void scenarioEReprocessesFromDay2PreservingOldRevisions() throws Exception {
        UUID areaId = scenarioACalculated("Cenário E");
        Map<String, Object> day1Before = persistedRow(areaId, DAY_1, 1);

        // corrige a ETP do dia 2: 6,25 -> 7,50 (ETc = 6,00)
        registerWeather(areaId, date(2), "7.50", "0");

        MvcResult result = reprocess(areaId, date(2)).andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();

        // resposta: dias 2 a 7, em ordem cronológica, revisão 2 corrente
        assertThat((Integer) JsonPath.read(json, "$.length()")).isEqualTo(6);
        for (int i = 0; i < 6; i++) {
            assertThat((String) JsonPath.read(json, "$[" + i + "].date")).isEqualTo(date(2 + i).toString());
            assertThat((Integer) JsonPath.read(json, "$[" + i + "].control.revision")).isEqualTo(2);
            assertThat((Boolean) JsonPath.read(json, "$[" + i + "].control.current")).isTrue();
        }
        // dia 2: AFDi 32, AFDf 26, I = 6, 1,0000 h
        assertThat(dec(json, "$[0].results.afdInitialMm")).isEqualByComparingTo("32");
        assertThat(dec(json, "$[0].results.etcMm")).isEqualByComparingTo("6");
        assertThat(dec(json, "$[0].results.afdFinalMm")).isEqualByComparingTo("26");
        assertThat(dec(json, "$[0].results.idealIrrigationMm")).isEqualByComparingTo("6");
        assertThat(dec(json, "$[0].results.operatingTime.hours")).isEqualByComparingTo("1.0000");
        assertThat(json).contains("\"hours\":1.0000");
        // dia 3: AFDi 26, AFDf 27, I = -1
        assertThat(dec(json, "$[1].results.afdInitialMm")).isEqualByComparingTo("26");
        assertThat(dec(json, "$[1].results.afdFinalMm")).isEqualByComparingTo("27");
        assertThat(dec(json, "$[1].results.idealIrrigationMm")).isEqualByComparingTo("-1");
        // dia 4: AFDi 27, AFDf 22, I = 5
        assertThat(dec(json, "$[2].results.afdInitialMm")).isEqualByComparingTo("27");
        assertThat(dec(json, "$[2].results.afdFinalMm")).isEqualByComparingTo("22");
        assertThat(dec(json, "$[2].results.idealIrrigationMm")).isEqualByComparingTo("5");

        // banco: valores sem arredondamento da revisão 2 corrente
        Map<String, Object> db2 = persistedRow(areaId, date(2), 2);
        assertThat((BigDecimal) db2.get("afd_final_mm")).isEqualByComparingTo("26");
        assertThat((BigDecimal) db2.get("ideal_irrigation_mm")).isEqualByComparingTo("6");
        assertThat((BigDecimal) db2.get("operating_time_hours")).isEqualByComparingTo("1");
        assertThat(db2.get("is_current")).isEqualTo(true);
        Map<String, Object> db3 = persistedRow(areaId, date(3), 2);
        assertThat((BigDecimal) db3.get("afd_initial_mm")).isEqualByComparingTo("26");
        assertThat((BigDecimal) db3.get("afd_final_mm")).isEqualByComparingTo("27");
        Map<String, Object> db4 = persistedRow(areaId, date(4), 2);
        assertThat((BigDecimal) db4.get("afd_initial_mm")).isEqualByComparingTo("27");
        assertThat((BigDecimal) db4.get("afd_final_mm")).isEqualByComparingTo("22");

        // revisões antigas preservadas, com os valores originais, e não correntes
        Map<String, Object> old2 = persistedRow(areaId, date(2), 1);
        assertThat(old2.get("is_current")).isEqualTo(false);
        assertThat((BigDecimal) old2.get("afd_final_mm")).isEqualByComparingTo("27");
        assertThat((BigDecimal) old2.get("operating_time_hours")).isEqualByComparingTo("0.8333333333");
        for (int day = 2; day <= 7; day++) {
            assertThat(persistedRow(areaId, date(day), 1).get("is_current")).isEqualTo(false);
        }

        // uma corrente por dia; 13 linhas no total (7 + 6)
        for (int day = 1; day <= 7; day++) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                    + "AND balance_date = ? AND is_current", Integer.class, areaId, Date.valueOf(date(day))))
                    .as("correntes do dia %d", day).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ?",
                Integer.class, areaId)).isEqualTo(13);

        // dia 1 intacto: mesma linha, ainda revisão 1 corrente
        assertThat(persistedRow(areaId, DAY_1, 1)).isEqualTo(day1Before);
        assertThat(day1Before.get("is_current")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                + "AND balance_date = ?", Integer.class, areaId, Date.valueOf(DAY_1))).isEqualTo(1);

        // histórico do dia 2: da revisão mais recente para a mais antiga
        mvc.perform(get(base(areaId) + "/" + date(2) + "/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].control.revision").value(2))
                .andExpect(jsonPath("$[0].control.current").value(true))
                .andExpect(jsonPath("$[0].results.afdFinalMm").value(26.0))
                .andExpect(jsonPath("$[1].control.revision").value(1))
                .andExpect(jsonPath("$[1].control.current").value(false))
                .andExpect(jsonPath("$[1].results.afdFinalMm").value(27.0));
        mvc.perform(get(base(areaId) + "/" + DAY_1 + "/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].control.revision").value(1));

        // a consulta por data devolve a corrente
        mvc.perform(get(base(areaId) + "/" + date(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.control.revision").value(2))
                .andExpect(jsonPath("$.results.afdFinalMm").value(27.0));
    }

    @Test
    void reprocessWithoutDataChangeReproducesTheSameValuesWithIncrementedRevision() throws Exception {
        UUID areaId = scenarioACalculated("Reprocesso idêntico");
        List<Map<String, Object>> before = currentRows(areaId);

        reprocess(areaId, date(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].control.revision").value(2));

        List<Map<String, Object>> after = currentRows(areaId);
        assertThat(after).hasSize(7);
        for (int i = 0; i < 7; i++) {
            Map<String, Object> b = before.get(i);
            Map<String, Object> a = after.get(i);
            for (String column : List.of("etc_mm", "afd_initial_mm", "afd_final_mm", "ideal_irrigation_mm",
                    "operating_time_hours", "cad_mm")) {
                assertThat((BigDecimal) a.get(column)).as("%s do dia %d", column, i + 1)
                        .isEqualByComparingTo((BigDecimal) b.get(column));
            }
            assertThat(a.get("irrigation_recommended")).isEqualTo(b.get("irrigation_recommended"));
            assertThat(a.get("cycle_day_tr")).isEqualTo(b.get("cycle_day_tr"));
            assertThat(a.get("revision")).isEqualTo(i == 0 ? 1 : 2);
        }
    }

    // ---------------------------------------------------------------- 422

    @Test
    void reprocessOfAnUncalculatedDateIsRejectedWith422() throws Exception {
        UUID areaId = scenarioACalculated("Data não calculada");
        List<Map<String, Object>> snapshot = allRows(areaId);

        reprocess(areaId, date(8)).andExpect(status().isUnprocessableEntity());

        assertThat(allRows(areaId)).isEqualTo(snapshot);
    }

    @Test
    void reprocessAbortsWithoutWritingWhenALaterDayViolatesPreconditions() throws Exception {
        UUID areaId = scenarioACalculated("Pré-condição violada");
        registerWeather(areaId, date(2), "7.50", "0");
        // o dia 5 perde a meteorologia
        jdbc.update("DELETE FROM daily_weather WHERE area_id = ? AND weather_date = ?",
                areaId, Date.valueOf(date(5)));
        List<Map<String, Object>> snapshot = allRows(areaId);

        reprocess(areaId, date(2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.date").value(date(5).toString()))
                .andExpect(jsonPath("$.preconditions[0].code").value("WEATHER_MISSING"));

        assertThat(allRows(areaId)).as("nada foi gravado").isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                + "AND is_current", Integer.class, areaId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT max(revision) FROM daily_water_balance WHERE area_id = ?",
                Integer.class, areaId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void concurrentReprocessRequestsNeverLeaveTwoCurrentRowsPerDay() throws Exception {
        UUID areaId = scenarioACalculated("Reprocesso concorrente");
        int successes = 0;
        for (int round = 0; round < 5; round++) {
            List<Integer> statuses = runConcurrently(
                    () -> reprocess(areaId, date(2)).andReturn().getResponse().getStatus(),
                    () -> reprocess(areaId, date(2)).andReturn().getResponse().getStatus());
            // cada requisição ou conclui ou é recusada pelo bloqueio da área (409)
            assertThat(statuses).allMatch(s -> s == 200 || s == 409);
            assertThat(statuses).contains(200);
            successes += (int) statuses.stream().filter(s -> s == 200).count();

            assertOneCurrentPerDay(areaId, 7);
            // a revisão corrente avança exatamente uma por reprocesso concluído
            assertThat(jdbc.queryForObject("SELECT max(revision) FROM daily_water_balance WHERE area_id = ? "
                    + "AND balance_date = ?", Integer.class, areaId, Date.valueOf(date(2))))
                    .isEqualTo(1 + successes);
        }
    }

    @Test
    void concurrentCalculationsOfTheSameDayYieldASingleCurrentRow() throws Exception {
        UUID areaId = scenarioACalculated("Cálculo concorrente");
        int rounds = 4;
        for (int round = 0; round < rounds; round++) {
            int day = 8 + round;
            registerDay(areaId, date(day), "6.00", "0", "0");
            int cycleDay = (day - 1) % 3 + 1;

            List<Integer> statuses = runConcurrently(
                    () -> calculate(areaId, date(day), cycleDay),
                    () -> calculate(areaId, date(day), cycleDay));

            assertThat(statuses.stream().filter(s -> s == 201).count()).as("sucessos %s", statuses).isEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 201 || s == 409 || s == 422);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                    + "AND balance_date = ?", Integer.class, areaId, Date.valueOf(date(day)))).isEqualTo(1);
            assertOneCurrentPerDay(areaId, day);
        }
    }

    @Test
    void concurrentCalculateAndReprocessNeverDuplicateCurrentRows() throws Exception {
        UUID areaId = scenarioACalculated("Cálculo e reprocesso");
        for (int round = 0; round < 3; round++) {
            int day = 8 + round;
            registerDay(areaId, date(day), "6.00", "0", "0");
            int cycleDay = (day - 1) % 3 + 1;

            List<Integer> statuses = runConcurrently(
                    () -> calculate(areaId, date(day), cycleDay),
                    () -> reprocess(areaId, date(2)).andReturn().getResponse().getStatus());

            assertThat(statuses).allMatch(s -> s == 200 || s == 201 || s == 409 || s == 422);
            assertOneCurrentPerDay(areaId, (int) jdbc.queryForObject(
                    "SELECT count(DISTINCT balance_date) FROM daily_water_balance WHERE area_id = ?",
                    Integer.class, areaId));
        }
    }

    // ---------------------------------------------------------------- auxiliares

    /** Dispara as duas tarefas ao mesmo tempo e devolve os códigos HTTP, na ordem das tarefas. */
    private static List<Integer> runConcurrently(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertOneCurrentPerDay(UUID areaId, int expectedDays) {
        assertThat(jdbc.queryForList("SELECT balance_date FROM daily_water_balance WHERE area_id = ? "
                + "AND is_current GROUP BY balance_date HAVING count(*) > 1", areaId))
                .as("dias com mais de uma corrente").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_water_balance WHERE area_id = ? "
                + "AND is_current", Integer.class, areaId)).isGreaterThanOrEqualTo(expectedDays);
    }

    private static LocalDate date(int day) {
        return DAY_1.plusDays(day - 1);
    }

    private static String base(UUID areaId) {
        return "/api/v1/areas/" + areaId + "/water-balance";
    }

    private static BigDecimal dec(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    private Map<String, Object> persistedRow(UUID areaId, LocalDate date, int revision) {
        return jdbc.queryForMap("SELECT * FROM daily_water_balance WHERE area_id = ? AND balance_date = ? "
                + "AND revision = ?", areaId, Date.valueOf(date), revision);
    }

    private List<Map<String, Object>> currentRows(UUID areaId) {
        return jdbc.queryForList("SELECT * FROM daily_water_balance WHERE area_id = ? AND is_current "
                + "ORDER BY balance_date", areaId);
    }

    private List<Map<String, Object>> allRows(UUID areaId) {
        return jdbc.queryForList("SELECT * FROM daily_water_balance WHERE area_id = ? "
                + "ORDER BY balance_date, revision", areaId);
    }

    private org.springframework.test.web.servlet.ResultActions reprocess(UUID areaId, LocalDate date)
            throws Exception {
        return mvc.perform(post(base(areaId) + "/reprocess").contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"));
    }

    /** Calcula um dia e devolve o código HTTP. */
    private int calculate(UUID areaId, LocalDate date, int cycleDay) throws Exception {
        return mvc.perform(post(base(areaId) + "/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"cycleDay\":" + cycleDay + "}"))
                .andReturn().getResponse().getStatus();
    }

    /** Monta a área do Cenário A, registra as entradas dos 7 dias e calcula todos (201). */
    private UUID scenarioACalculated(String areaName) throws Exception {
        UUID areaId = setUpArea(areaName);
        for (int day = 1; day <= 7; day++) {
            registerDay(areaId, date(day), ETP[day - 1], P[day - 1], IR[day - 1]);
        }
        for (int day = 1; day <= 7; day++) {
            assertThat(calculate(areaId, date(day), CYCLE_DAY[day - 1])).isEqualTo(201);
        }
        return areaId;
    }

    /** Cadastro mínimo (solo, cultura, área) e plano iniciando no dia 1, com F = 0,50. */
    private UUID setUpArea(String areaName) throws Exception {
        UUID soilId = create("/api/v1/soil-profiles", "{\"soilType\":\"Cenário\","
                + "\"fieldCapacityGravimetricPct\":30,\"permanentWiltingPointGravimetricPct\":15,"
                + "\"bulkDensity\":1.2}");
        UUID cropId = create("/api/v1/crops", "{\"name\":\"Cultura " + areaName + "\",\"kc\":0.8,"
                + "\"rootDepthMm\":400}");
        UUID areaId = create("/api/v1/areas", "{\"name\":\"" + areaName + "\"}");
        create("/api/v1/areas/" + areaId + "/management-plans", "{\"cropId\":\"" + cropId + "\","
                + "\"soilProfileId\":\"" + soilId + "\",\"startDate\":\"" + DAY_1 + "\","
                + "\"fFactor\":0.50,\"sprinklerRateMmPerHour\":6}");
        return areaId;
    }

    private void registerWeather(UUID areaId, LocalDate date, String etp, String precipitation) throws Exception {
        mvc.perform(post("/api/v1/areas/" + areaId + "/daily-weather").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"etpMm\":" + etp
                                + ",\"precipitationMm\":" + precipitation + "}"))
                .andExpect(status().isOk());
    }

    private void registerDay(UUID areaId, LocalDate date, String etp, String precipitation, String irrigation)
            throws Exception {
        registerWeather(areaId, date, etp, precipitation);
        mvc.perform(post("/api/v1/areas/" + areaId + "/applied-irrigation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"appliedIrrigationMm\":" + irrigation + "}"))
                .andExpect(status().isOk());
    }

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
