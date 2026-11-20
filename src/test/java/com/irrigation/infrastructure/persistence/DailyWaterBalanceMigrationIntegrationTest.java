package com.irrigation.infrastructure.persistence;

import com.irrigation.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
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
 * Migração V5 para V6 sobre dados preexistentes, índices, CHECKs e triggers de imutabilidade de
 * {@code daily_water_balance} (Req 12.5, 12.7, 12.8, 12.9, 12.10, 16.4).
 *
 * <p>Cada teste usa um banco próprio no mesmo container, para migrar por etapas sem afetar o schema
 * compartilhado pelos demais testes.
 */
class DailyWaterBalanceMigrationIntegrationTest extends PostgresIntegrationTest {

    private static final List<String> OLD_TABLES = List.of("areas", "crops", "crop_stages", "soil_profiles",
            "recommendations", "management_plans", "daily_weather", "applied_irrigation");

    private static final String IMMUTABLE = "immutable_daily_water_balance";

    private String url;
    private JdbcTemplate db;

    private final UUID areaId = UUID.randomUUID();
    private final UUID otherAreaId = UUID.randomUUID();
    private final UUID soilId = UUID.randomUUID();
    private final UUID cropId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void createIsolatedDatabase() {
        String name = "v6_" + UUID.randomUUID().toString().replace("-", "");
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

    /** Dados no formato da V5: cadastros, plano, meteorologia, irrigação real e recomendação. */
    private void insertPreexistingData() {
        db.update("INSERT INTO areas (id, name, area_ha, latitude, longitude, general_characteristics, "
                + "irrigation_system_type, system_flow_rate, irrigation_efficiency) "
                + "VALUES (?, 'Talhão completo', 12.50, -23.5, -46.6, 'Argiloso', 'DRIP', 150.00, 0.9000)", areaId);
        db.update("INSERT INTO areas (id, name) VALUES (?, 'Só o nome')", otherAreaId);
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
        db.update("INSERT INTO management_plans (id, area_id, crop_id, soil_profile_id, start_date, "
                + "f_factor, sprinkler_rate_mm_h) VALUES (?, ?, ?, ?, '2026-01-01', 0.5000, 6.0000)",
                planId, areaId, cropId, soilId);
        db.update("INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, '2026-01-01', 5.250, 0)", areaId);
        db.update("INSERT INTO daily_weather (area_id, weather_date, etp_mm, precipitation_mm) "
                + "VALUES (?, '2026-01-02', 6.125, 2.500)", areaId);
        db.update("INSERT INTO applied_irrigation (area_id, irrigation_date, applied_irrigation_mm) "
                + "VALUES (?, '2026-01-01', 0)", areaId);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : OLD_TABLES) {
            String order = table.equals("crop_stages") ? "stage_order, crop_id" : "id";
            result.put(table, db.queryForList("SELECT * FROM " + table + " ORDER BY " + order));
        }
        return result;
    }

    private List<Map<String, Object>> checksumsOfV1ToV5() {
        return db.queryForList("SELECT version, checksum, description FROM flyway_schema_history "
                + "WHERE version IN ('1', '2', '3', '4', '5') ORDER BY installed_rank");
    }

    private int balanceCount() {
        return db.queryForObject("SELECT count(*) FROM daily_water_balance", Integer.class);
    }

    /**
     * Insere um balanço válido com valores fixos. Só data, revisão, corrente, dia do ciclo e tempo de
     * funcionamento variam, porque são o que os CHECKs e os índices avaliam.
     */
    private UUID insertBalance(UUID area, String date, int revision, boolean current, int cycleDay,
                               String operatingTimeHours) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO daily_water_balance (id, area_id, balance_date, crop_id, soil_profile_id, "
                + "management_plan_id, cycle_day_tr, etp_mm, precipitation_mm, applied_irrigation_mm, "
                + "field_capacity_pct, wilting_point_pct, bulk_density_g_cm3, root_depth_mm, kc, f_factor, "
                + "sprinkler_rate_mm_h, etc_mm, cad_mm, afd_initial_mm, afd_final_mm, ideal_irrigation_mm, "
                + "irrigation_recommended, operating_time_hours, methodology_id, methodology_version, "
                + "revision, is_current) VALUES (?, ?, ?::date, ?, ?, ?, ?, 5.000, 0.000, 0.000, 30.00, 15.00, "
                + "1.200, 400.00, 0.80, 0.5000, 6.0000, 4, 72, 36, 32, 4, true, ?::numeric, 'water-balance', "
                + "'1.0', ?, ?)",
                id, area, date, cropId, soilId, planId, cycleDay, operatingTimeHours, revision, current);
        return id;
    }

    private UUID insertBalance(String date, int revision, boolean current) {
        return insertBalance(areaId, date, revision, current, 1, "0.6666666667");
    }

    private void assertViolation(Runnable statement, String name) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(name);
    }

    /** Migra até a V6 e cadastra os dados de referência que as chaves estrangeiras exigem. */
    private void prepareV6() {
        migrateTo("6");
        insertPreexistingData();
    }

    // ------------------------------------------------------------------------------------
    // Migração com dados preexistentes
    // ------------------------------------------------------------------------------------

    @Test
    void migratingFromV5ToV6KeepsEveryRowAndEveryOldColumnValue() {
        migrateTo("5");
        insertPreexistingData();
        Map<String, List<Map<String, Object>>> before = snapshot();
        assertThat(before.get("areas")).hasSize(2);
        assertThat(before.get("soil_profiles")).hasSize(2);
        assertThat(before.get("crops")).hasSize(2);
        assertThat(before.get("crop_stages")).hasSize(2);
        assertThat(before.get("recommendations")).hasSize(1);
        assertThat(before.get("management_plans")).hasSize(1);
        assertThat(before.get("daily_weather")).hasSize(2);
        assertThat(before.get("applied_irrigation")).hasSize(1);

        migrateTo("6");

        Map<String, List<Map<String, Object>>> after = snapshot();
        for (String table : OLD_TABLES) {
            assertThat(after.get(table)).as("linhas e valores de %s", table).isEqualTo(before.get(table));
        }
        assertThat(balanceCount()).as("daily_water_balance começa vazia").isZero();
    }

    @Test
    void checksumsOfV1ToV5AreUnchangedAndHistoryIsOneToSix() {
        migrateTo("5");
        List<Map<String, Object>> before = checksumsOfV1ToV5();
        assertThat(before).hasSize(5);
        assertThat(before).allSatisfy(row -> assertThat(row.get("checksum")).isNotNull());

        migrateTo("6");

        assertThat(checksumsOfV1ToV5()).isEqualTo(before);
        List<Map<String, Object>> history = db.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void newTableAcceptsReferencesToPreexistingRows() {
        migrateTo("5");
        insertPreexistingData();
        migrateTo("6");

        insertBalance("2026-01-01", 1, true);

        assertThat(balanceCount()).isEqualTo(1);
        assertThat(snapshot().get("areas")).hasSize(2);
    }

    // ------------------------------------------------------------------------------------
    // Índices
    // ------------------------------------------------------------------------------------

    @Test
    void indexesExistWithTheExpectedDefinitions() {
        migrateTo("6");

        Map<String, String> definitions = new LinkedHashMap<>();
        db.queryForList("SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'daily_water_balance'")
                .forEach(row -> definitions.put((String) row.get("indexname"), (String) row.get("indexdef")));

        assertThat(definitions).containsKeys("uq_daily_water_balance_current", "uq_daily_water_balance_revision",
                "idx_daily_water_balance_area_date");
        assertThat(definitions.get("uq_daily_water_balance_current"))
                .contains("UNIQUE").contains("(area_id, balance_date)").contains("WHERE is_current");
        assertThat(definitions.get("uq_daily_water_balance_revision"))
                .contains("UNIQUE").contains("(area_id, balance_date, revision)");
        assertThat(definitions.get("idx_daily_water_balance_area_date"))
                .doesNotContain("UNIQUE").contains("(area_id, balance_date)");
    }

    @Test
    void secondCurrentRevisionForTheSameAreaAndDateIsRejectedByThePartialUniqueIndex() {
        prepareV6();
        insertBalance("2026-01-01", 1, true);

        assertViolation(() -> insertBalance("2026-01-01", 2, true), "uq_daily_water_balance_current");

        assertThat(balanceCount()).isEqualTo(1);
    }

    @Test
    void nonCurrentRevisionsAndOtherAreasOrDatesDoNotConflictWithTheCurrentOne() {
        prepareV6();

        assertThatCode(() -> {
            insertBalance("2026-01-01", 1, false);
            insertBalance("2026-01-01", 2, false);
            insertBalance("2026-01-01", 3, true);
            insertBalance("2026-01-02", 1, true);
            insertBalance(otherAreaId, "2026-01-01", 1, true, 1, "0");
        }).doesNotThrowAnyException();

        assertThat(db.queryForObject("SELECT count(*) FROM daily_water_balance WHERE is_current", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void sameRevisionForTheSameAreaAndDateIsRejectedEvenWhenNotCurrent() {
        prepareV6();
        insertBalance("2026-01-01", 1, false);

        assertViolation(() -> insertBalance("2026-01-01", 1, false), "uq_daily_water_balance_revision");

        assertThat(balanceCount()).isEqualTo(1);
    }

    @Test
    void demotingTheCurrentRevisionFreesTheDateForANewCurrentOne() {
        prepareV6();
        insertBalance("2026-01-01", 1, true);

        db.update("UPDATE daily_water_balance SET is_current = false WHERE area_id = ?", areaId);
        insertBalance("2026-01-01", 2, true);

        assertThat(db.queryForList("SELECT revision FROM daily_water_balance WHERE is_current", Integer.class))
                .containsExactly(2);
    }

    // ------------------------------------------------------------------------------------
    // CHECKs
    // ------------------------------------------------------------------------------------

    @Test
    void checksAcceptTheBoundaryValues() {
        prepareV6();

        assertThatCode(() -> insertBalance(areaId, "2026-01-01", 1, true, 1, "0")).doesNotThrowAnyException();
    }

    @Test
    void cycleDayBelowOneIsRejected() {
        prepareV6();

        assertViolation(() -> insertBalance(areaId, "2026-01-01", 1, true, 0, "0"),
                "chk_daily_water_balance_cycle_day");
        assertViolation(() -> insertBalance(areaId, "2026-01-01", 1, true, -3, "0"),
                "chk_daily_water_balance_cycle_day");
        assertThat(balanceCount()).isZero();
    }

    @Test
    void negativeOperatingTimeIsRejectedAndZeroIsAccepted() {
        prepareV6();

        assertViolation(() -> insertBalance(areaId, "2026-01-01", 1, true, 1, "-0.0000000001"),
                "chk_daily_water_balance_operating_time");
        insertBalance(areaId, "2026-01-01", 1, true, 1, "0");
        assertThat(balanceCount()).isEqualTo(1);
    }

    @Test
    void revisionBelowOneIsRejected() {
        prepareV6();

        assertViolation(() -> insertBalance("2026-01-01", 0, true), "chk_daily_water_balance_revision");
        assertViolation(() -> insertBalance("2026-01-01", -1, false), "chk_daily_water_balance_revision");
        assertThat(balanceCount()).isZero();
    }

    @Test
    void foreignKeysRequireExistingAreaCropSoilAndPlan() {
        prepareV6();
        String insert = "INSERT INTO daily_water_balance (area_id, balance_date, crop_id, soil_profile_id, "
                + "management_plan_id, cycle_day_tr, etp_mm, precipitation_mm, applied_irrigation_mm, "
                + "field_capacity_pct, wilting_point_pct, bulk_density_g_cm3, root_depth_mm, kc, f_factor, "
                + "sprinkler_rate_mm_h, etc_mm, cad_mm, afd_initial_mm, afd_final_mm, ideal_irrigation_mm, "
                + "irrigation_recommended, operating_time_hours, methodology_id, methodology_version, "
                + "revision, is_current) VALUES (?, '2026-01-01', ?, ?, ?, 1, 5, 0, 0, 30, 15, 1.2, 400, 0.8, "
                + "0.5, 6, 4, 72, 36, 32, 4, true, 0, 'water-balance', '1.0', 1, true)";

        assertViolation(() -> db.update(insert, UUID.randomUUID(), cropId, soilId, planId),
                "fk_daily_water_balance_area");
        assertViolation(() -> db.update(insert, areaId, UUID.randomUUID(), soilId, planId),
                "fk_daily_water_balance_crop");
        assertViolation(() -> db.update(insert, areaId, cropId, UUID.randomUUID(), planId),
                "fk_daily_water_balance_soil_profile");
        assertViolation(() -> db.update(insert, areaId, cropId, soilId, UUID.randomUUID()),
                "fk_daily_water_balance_plan");
    }

    @Test
    void referencedRegistrationsCannotBeDeletedWhileBalancesExist() {
        prepareV6();
        insertBalance("2026-01-01", 1, true);

        // O plano referencia área, cultura e solo, e a FK do plano (V5) dispara antes da FK do balanço (V6):
        // enquanto o plano existir, é a FK do plano que recusa a exclusão.
        assertViolation(() -> db.update("DELETE FROM management_plans WHERE id = ?", planId),
                "fk_daily_water_balance_plan");
        assertViolation(() -> db.update("DELETE FROM soil_profiles WHERE id = ?", soilId),
                "fk_management_plans_soil_profile");
        assertViolation(() -> db.update("DELETE FROM crops WHERE id = ?", cropId), "fk_management_plans_crop");
        // Outras referências à área (recomendação, meteorologia e irrigação real) são removidas para isolar as FKs
        db.update("DELETE FROM recommendations");
        db.update("DELETE FROM daily_weather");
        db.update("DELETE FROM applied_irrigation");
        assertViolation(() -> db.update("DELETE FROM areas WHERE id = ?", areaId), "fk_management_plans_area");

        // Sem as FKs do plano (banco isolado), as FKs do balanço também recusam, com ON DELETE RESTRICT
        db.execute("ALTER TABLE management_plans DROP CONSTRAINT fk_management_plans_soil_profile");
        db.execute("ALTER TABLE management_plans DROP CONSTRAINT fk_management_plans_crop");
        db.execute("ALTER TABLE management_plans DROP CONSTRAINT fk_management_plans_area");
        assertViolation(() -> db.update("DELETE FROM soil_profiles WHERE id = ?", soilId),
                "fk_daily_water_balance_soil_profile");
        assertViolation(() -> db.update("DELETE FROM crops WHERE id = ?", cropId), "fk_daily_water_balance_crop");
        assertViolation(() -> db.update("DELETE FROM areas WHERE id = ?", areaId), "fk_daily_water_balance_area");
        assertThat(balanceCount()).isEqualTo(1);
    }

    @Test
    void resultColumnsKeepTheExactScaleOfThirteenDecimalPlaces() {
        prepareV6();
        // UPDATE direto não é aceito; o valor é gravado já no INSERT
        UUID second = UUID.randomUUID();
        db.update("INSERT INTO daily_water_balance (id, area_id, balance_date, crop_id, soil_profile_id, "
                + "management_plan_id, cycle_day_tr, etp_mm, precipitation_mm, applied_irrigation_mm, "
                + "field_capacity_pct, wilting_point_pct, bulk_density_g_cm3, root_depth_mm, kc, f_factor, "
                + "sprinkler_rate_mm_h, etc_mm, cad_mm, afd_initial_mm, afd_final_mm, ideal_irrigation_mm, "
                + "irrigation_recommended, operating_time_hours, methodology_id, methodology_version, "
                + "revision, is_current) VALUES (?, ?, '2026-01-02', ?, ?, ?, 1, 5, 0, 0, 30, 15, 1.2, 400, 0.8, "
                + "0.5, 6, 4.0000000000001, 72, 36.1234567890123, -27.9876543210987, 64.1111111111110, true, "
                + "0, 'water-balance', '1.0', 1, true)",
                second, areaId, cropId, soilId, planId);

        Map<String, Object> row = db.queryForMap(
                "SELECT afd_initial_mm, afd_final_mm, etc_mm FROM daily_water_balance WHERE id = ?", second);
        assertThat((BigDecimal) row.get("afd_initial_mm")).isEqualTo(new BigDecimal("36.1234567890123"));
        assertThat((BigDecimal) row.get("afd_final_mm")).isEqualTo(new BigDecimal("-27.9876543210987"));
        assertThat((BigDecimal) row.get("etc_mm")).isEqualTo(new BigDecimal("4.0000000000001"));
    }

    // ------------------------------------------------------------------------------------
    // Trigger de imutabilidade
    // ------------------------------------------------------------------------------------

    @Test
    void directUpdateOfAnyValueColumnIsRejected() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, true);
        Map<String, Object> before = db.queryForMap("SELECT * FROM daily_water_balance WHERE id = ?", id);

        for (String assignment : List.of("etc_mm = 99", "afd_final_mm = 1", "etp_mm = 1", "revision = 7",
                "balance_date = '2026-02-02'", "methodology_id = 'outra'", "no_recommendation_reason = 'X'",
                "cycle_day_tr = 3", "irrigation_recommended = false")) {
            assertViolation(() -> db.update("UPDATE daily_water_balance SET " + assignment + " WHERE id = ?", id),
                    IMMUTABLE);
        }

        assertThat(db.queryForMap("SELECT * FROM daily_water_balance WHERE id = ?", id)).isEqualTo(before);
    }

    @Test
    void directDeleteIsRejected() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, true);
        insertBalance("2026-01-01", 2, false);

        assertViolation(() -> db.update("DELETE FROM daily_water_balance WHERE id = ?", id), IMMUTABLE);
        assertViolation(() -> db.update("DELETE FROM daily_water_balance"), IMMUTABLE);

        assertThat(balanceCount()).isEqualTo(2);
    }

    @Test
    void rejectedErrorsUseSqlStateIntegrityConstraintViolation() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, true);

        Throwable update = org.assertj.core.api.Assertions.catchThrowable(
                () -> db.update("UPDATE daily_water_balance SET etc_mm = 1 WHERE id = ?", id));
        Throwable delete = org.assertj.core.api.Assertions.catchThrowable(
                () -> db.update("DELETE FROM daily_water_balance WHERE id = ?", id));

        for (Throwable error : List.of(update, delete)) {
            Throwable root = error;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) root).getSQLState()).isEqualTo("23000");
        }
    }

    @Test
    void onlyTheTransitionOfIsCurrentFromTrueToFalseIsAccepted() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, true);
        Map<String, Object> before = db.queryForMap("SELECT * FROM daily_water_balance WHERE id = ?", id);

        int updated = db.update("UPDATE daily_water_balance SET is_current = false WHERE id = ?", id);

        assertThat(updated).isEqualTo(1);
        Map<String, Object> after = db.queryForMap("SELECT * FROM daily_water_balance WHERE id = ?", id);
        Map<String, Object> expected = new LinkedHashMap<>(before);
        expected.put("is_current", false);
        assertThat(after).isEqualTo(expected);
    }

    @Test
    void reversingIsCurrentFromFalseToTrueIsRejected() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, false);

        assertViolation(() -> db.update("UPDATE daily_water_balance SET is_current = true WHERE id = ?", id),
                IMMUTABLE);

        assertThat(db.queryForObject("SELECT is_current FROM daily_water_balance WHERE id = ?", Boolean.class, id))
                .isFalse();
    }

    @Test
    void demotingIsCurrentTogetherWithAnotherChangeIsRejected() {
        prepareV6();
        UUID id = insertBalance("2026-01-01", 1, true);

        assertViolation(() -> db.update(
                "UPDATE daily_water_balance SET is_current = false, afd_final_mm = 0 WHERE id = ?", id), IMMUTABLE);

        Map<String, Object> row = db.queryForMap("SELECT is_current, afd_final_mm FROM daily_water_balance "
                + "WHERE id = ?", id);
        assertThat(row.get("is_current")).isEqualTo(true);
        assertThat((BigDecimal) row.get("afd_final_mm")).isEqualByComparingTo("32");
    }

    @Test
    void updatesThatKeepIsCurrentUnchangedAreRejectedEvenWithoutAnyChange() {
        prepareV6();
        UUID current = insertBalance("2026-01-01", 1, true);
        UUID old = insertBalance("2026-01-01", 2, false);

        assertViolation(() -> db.update("UPDATE daily_water_balance SET is_current = true WHERE id = ?", current),
                IMMUTABLE);
        assertViolation(() -> db.update("UPDATE daily_water_balance SET is_current = false WHERE id = ?", old),
                IMMUTABLE);
    }

    @Test
    void batchDemotionOfSeveralRowsIsAcceptedAndIsAtomicWhenOneRowIsNotEligible() {
        prepareV6();
        insertBalance("2026-01-01", 1, true);
        insertBalance("2026-01-02", 1, true);
        insertBalance("2026-01-03", 1, true);

        int updated = db.update("UPDATE daily_water_balance SET is_current = false "
                + "WHERE area_id = ? AND balance_date >= '2026-01-02' AND is_current", areaId);

        assertThat(updated).isEqualTo(2);
        assertThat(db.queryForList("SELECT balance_date::text FROM daily_water_balance WHERE is_current",
                String.class)).containsExactly("2026-01-01");

        // atingir uma linha já não corrente derruba o comando inteiro
        assertViolation(() -> db.update("UPDATE daily_water_balance SET is_current = false WHERE area_id = ?",
                areaId), IMMUTABLE);
        assertThat(db.queryForObject("SELECT count(*) FROM daily_water_balance WHERE is_current", Integer.class))
                .isEqualTo(1);
    }
}
