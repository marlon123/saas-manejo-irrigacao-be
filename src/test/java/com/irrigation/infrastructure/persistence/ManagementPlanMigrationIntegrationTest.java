package com.irrigation.infrastructure.persistence;

import com.irrigation.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migração V4 para V5 sobre dados preexistentes, CHECKs, índice único parcial e UNIQUEs
 * (Req 4.7, 4.8, 5.7, 6.7, 16.4).
 *
 * <p>Cada teste usa um banco próprio no mesmo container: migra até a V4, insere dados e
 * migra até a V5. A V5 só cria tabelas novas; nenhuma coluna ou linha antiga pode mudar.
 */
class ManagementPlanMigrationIntegrationTest extends PostgresIntegrationTest {

    private static final List<String> OLD_TABLES =
            List.of("areas", "crops", "crop_stages", "soil_profiles", "recommendations");

    private String url;
    private JdbcTemplate db;

    private final UUID areaId = UUID.randomUUID();
    private final UUID soilId = UUID.randomUUID();
    private final UUID cropId = UUID.randomUUID();

    @BeforeEach
    void createIsolatedDatabase() {
        String name = "v5_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + name);
        url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        db = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    /** Dados no formato da V4 (inclui as colunas novas da V4: kc, profundidade e gravimétrico). */
    private void insertPreexistingData() {
        db.update("INSERT INTO areas (id, name, area_ha, latitude, longitude, general_characteristics, "
                + "irrigation_system_type, system_flow_rate, irrigation_efficiency) "
                + "VALUES (?, 'Talhão completo', 12.50, -23.5, -46.6, 'Argiloso', 'DRIP', 150.00, 0.9000)", areaId);
        db.update("INSERT INTO areas (name) VALUES ('Só o nome')");
        db.update("INSERT INTO soil_profiles (id, soil_type, field_capacity, permanent_wilting_point, bulk_density, "
                + "effective_root_depth, available_water, soil_water_unit, bulk_density_unit, root_depth_unit, "
                + "available_water_unit, field_capacity_gravimetric_pct, permanent_wilting_point_gravimetric_pct) "
                + "VALUES (?, 'Argiloso', 0.3500, 0.1800, 1.350, 600.00, 102.00, 'VOLUMETRIC_WATER_CONTENT', "
                + "'GRAMS_PER_CUBIC_CENTIMETER', 'MILLIMETERS', 'MILLIMETERS', 28.50, 14.20)", soilId);
        db.update("INSERT INTO soil_profiles (soil_type, bulk_density) VALUES ('Mínimo', 1.300)");
        db.update("INSERT INTO crops (id, name, variety, cultivation_cycle_days, kc, root_depth_mm) "
                + "VALUES (?, 'Milho', 'Híbrido', 120, 1.15, 450.50)", cropId);
        db.update("INSERT INTO crops (name) VALUES ('Feijão')");
        db.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Inicial', 0.40, 1)", cropId);
        db.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Pleno', 1.20, 2)", cropId);
        db.update("INSERT INTO recommendations (area_id, date_time, input_data, results, methodology_id, "
                + "methodology_version) VALUES (?, '2026-01-10 08:00:00', '{\"a\": 1}'::jsonb, "
                + "'{\"b\": [1, 2]}'::jsonb, 'fake', '1.0')", areaId);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : OLD_TABLES) {
            String order = table.equals("crop_stages") ? "stage_order, crop_id" : "id";
            result.put(table, db.queryForList("SELECT * FROM " + table + " ORDER BY " + order));
        }
        return result;
    }

    private List<Map<String, Object>> checksumsOfV1ToV4() {
        return db.queryForList("SELECT version, checksum, description FROM flyway_schema_history "
                + "WHERE version IN ('1', '2', '3', '4') ORDER BY installed_rank");
    }

    private void insertPlan(UUID area, String start, String end, String factor, String rate) {
        db.update("INSERT INTO management_plans (area_id, crop_id, soil_profile_id, start_date, end_date, "
                        + "f_factor, sprinkler_rate_mm_h) VALUES (?, ?, ?, ?::date, ?::date, ?::numeric, ?::numeric)",
                area, cropId, soilId, start, end, factor, rate);
    }

    private void assertViolation(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    // ------------------------------------------------------------------------------------
    // Migração com dados preexistentes
    // ------------------------------------------------------------------------------------

    @Test
    void migratingFromV4ToV5KeepsEveryRowAndEveryOldColumnValue() {
        migrateTo("4");
        insertPreexistingData();
        Map<String, List<Map<String, Object>>> before = snapshot();
        assertThat(before.get("areas")).hasSize(2);
        assertThat(before.get("soil_profiles")).hasSize(2);
        assertThat(before.get("crops")).hasSize(2);
        assertThat(before.get("crop_stages")).hasSize(2);
        assertThat(before.get("recommendations")).hasSize(1);

        migrateTo("5");

        Map<String, List<Map<String, Object>>> after = snapshot();
        for (String table : OLD_TABLES) {
            assertThat(after.get(table)).as("linhas e valores de %s", table).isEqualTo(before.get(table));
        }
        for (String table : List.of("management_plans", "daily_weather", "applied_irrigation")) {
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as("%s começa vazia", table).isZero();
        }
    }

    @Test
    void checksumsOfV1ToV4AreUnchangedAndV5IsRecorded() {
        migrateTo("4");
        List<Map<String, Object>> before = checksumsOfV1ToV4();
        assertThat(before).hasSize(4);
        assertThat(before).allSatisfy(row -> assertThat(row.get("checksum")).isNotNull());

        migrateTo("5");

        assertThat(checksumsOfV1ToV4()).isEqualTo(before);
        List<Map<String, Object>> history = db.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void newTablesAcceptReferencesToPreexistingRows() {
        migrateTo("4");
        insertPreexistingData();
        migrateTo("5");

        insertPlan(areaId, "2026-01-01", null, "0.5000", "10");
        db.update("INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, '2026-01-02', 5.250, 0)", areaId);
        db.update("INSERT INTO applied_irrigation (area_id, irrigation_date, applied_irrigation_mm) "
                + "VALUES (?, '2026-01-02', 0)", areaId);

        assertThat(db.queryForObject("SELECT count(*) FROM management_plans", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM daily_weather", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM applied_irrigation", Integer.class)).isEqualTo(1);
        assertThat(snapshot().get("areas")).hasSize(2);
    }

    // ------------------------------------------------------------------------------------
    // CHECKs de management_plans
    // ------------------------------------------------------------------------------------

    @Test
    void planChecksAcceptTheBoundaryValues() {
        migrateTo("5");
        insertPreexistingData();

        assertThatCode(() -> {
            insertPlan(areaId, "2026-01-01", "2026-01-01", "1.0000", "500.0000");
            insertPlan(areaId, "2026-02-01", "2026-02-10", "0.0001", "0.0001");
        }).doesNotThrowAnyException();
    }

    @Test
    void planFactorOutsideZeroExclusiveToOneInclusiveIsRejected() {
        migrateTo("5");
        insertPreexistingData();

        for (String invalid : List.of("0", "-0.5", "1.0001", "2")) {
            assertViolation(() -> insertPlan(areaId, "2026-01-01", null, invalid, "10"),
                    "chk_management_plans_f_factor");
        }
    }

    @Test
    void planSprinklerRateOutsideZeroExclusiveToFiveHundredInclusiveIsRejected() {
        migrateTo("5");
        insertPreexistingData();

        for (String invalid : List.of("0", "-1", "500.0001", "1000")) {
            assertViolation(() -> insertPlan(areaId, "2026-01-01", null, "0.5", invalid),
                    "chk_management_plans_sprinkler_rate");
        }
    }

    @Test
    void planEndDateBeforeStartDateIsRejected() {
        migrateTo("5");
        insertPreexistingData();

        assertViolation(() -> insertPlan(areaId, "2026-01-10", "2026-01-09", "0.5", "10"),
                "chk_management_plans_dates");
    }

    @Test
    void planRequiresExistingAreaCropAndSoilProfile() {
        migrateTo("5");
        insertPreexistingData();
        String insert = "INSERT INTO management_plans (area_id, crop_id, soil_profile_id, start_date, "
                + "f_factor, sprinkler_rate_mm_h) VALUES (?, ?, ?, '2026-01-01', 0.5, 10)";

        assertViolation(() -> db.update(insert, UUID.randomUUID(), cropId, soilId), "fk_management_plans_area");
        assertViolation(() -> db.update(insert, areaId, UUID.randomUUID(), soilId), "fk_management_plans_crop");
        assertViolation(() -> db.update(insert, areaId, cropId, UUID.randomUUID()),
                "fk_management_plans_soil_profile");
    }

    // ------------------------------------------------------------------------------------
    // Índice único parcial do plano ativo
    // ------------------------------------------------------------------------------------

    @Test
    void secondActivePlanForTheSameAreaIsRejectedByThePartialUniqueIndex() {
        migrateTo("5");
        insertPreexistingData();
        insertPlan(areaId, "2026-01-01", null, "0.5", "10");

        assertViolation(() -> insertPlan(areaId, "2026-03-01", null, "0.6", "12"), "uq_management_plans_active_area");
        assertThat(db.queryForObject("SELECT count(*) FROM management_plans", Integer.class)).isEqualTo(1);
    }

    @Test
    void closedPlansAndOtherAreasDoNotConflictWithTheActivePlan() {
        migrateTo("5");
        insertPreexistingData();
        UUID otherArea = db.queryForObject("SELECT id FROM areas WHERE name = 'Só o nome'", UUID.class);

        insertPlan(areaId, "2025-01-01", "2025-06-30", "0.5", "10");
        insertPlan(areaId, "2025-07-01", "2025-12-31", "0.5", "10");
        insertPlan(areaId, "2026-01-01", null, "0.5", "10");
        insertPlan(otherArea, "2026-01-01", null, "0.5", "10");

        assertThat(db.queryForObject("SELECT count(*) FROM management_plans WHERE end_date IS NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void closingTheActivePlanFreesTheAreaForANewActivePlan() {
        migrateTo("5");
        insertPreexistingData();
        insertPlan(areaId, "2026-01-01", null, "0.5", "10");

        db.update("UPDATE management_plans SET end_date = '2026-02-28' WHERE area_id = ?", areaId);
        insertPlan(areaId, "2026-03-01", null, "0.5", "10");

        assertThat(db.queryForObject("SELECT count(*) FROM management_plans WHERE end_date IS NULL", Integer.class))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------
    // daily_weather e applied_irrigation
    // ------------------------------------------------------------------------------------

    @Test
    void weatherChecksRejectNegativeEtpAndPrecipitationAndAcceptZero() {
        migrateTo("5");
        insertPreexistingData();
        String insert = "INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, ?::date, ?::numeric, ?::numeric)";

        db.update(insert, areaId, "2026-01-01", "0", "0");

        assertViolation(() -> db.update(insert, areaId, "2026-01-02", "-0.001", "0"), "chk_daily_weather_etp");
        assertViolation(() -> db.update(insert, areaId, "2026-01-03", "0", "-0.001"),
                "chk_daily_weather_precipitation");
    }

    @Test
    void appliedIrrigationCheckRejectsNegativeAndAcceptsZero() {
        migrateTo("5");
        insertPreexistingData();
        String insert = "INSERT INTO applied_irrigation (area_id, irrigation_date, applied_irrigation_mm) "
                + "VALUES (?, ?::date, ?::numeric)";

        db.update(insert, areaId, "2026-01-01", "0");

        assertViolation(() -> db.update(insert, areaId, "2026-01-02", "-0.001"), "chk_applied_irrigation_mm");
    }

    @Test
    void weatherAndIrrigationAreUniquePerAreaAndDate() {
        migrateTo("5");
        insertPreexistingData();
        UUID otherArea = db.queryForObject("SELECT id FROM areas WHERE name = 'Só o nome'", UUID.class);
        String weather = "INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, '2026-01-01', 5, 1)";
        String irrigation = "INSERT INTO applied_irrigation (area_id, irrigation_date, applied_irrigation_mm) "
                + "VALUES (?, '2026-01-01', 10)";
        db.update(weather, areaId);
        db.update(irrigation, areaId);

        assertViolation(() -> db.update(weather, areaId), "uq_daily_weather_area_date");
        assertViolation(() -> db.update(irrigation, areaId), "uq_applied_irrigation_area_date");
        // outra área na mesma data não conflita
        db.update(weather, otherArea);
        db.update(irrigation, otherArea);
        assertThat(db.queryForObject("SELECT count(*) FROM daily_weather", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM applied_irrigation", Integer.class)).isEqualTo(2);
    }

    @Test
    void numericColumnsKeepThreeDecimalPlaces() {
        migrateTo("5");
        insertPreexistingData();
        db.update("INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, '2026-01-01', 12.345, 0.001)", areaId);

        Map<String, Object> row = db.queryForMap("SELECT etp_mm, precipitation_mm FROM daily_weather");

        assertThat((BigDecimal) row.get("etp_mm")).isEqualTo(new BigDecimal("12.345"));
        assertThat((BigDecimal) row.get("precipitation_mm")).isEqualTo(new BigDecimal("0.001"));
    }
}
