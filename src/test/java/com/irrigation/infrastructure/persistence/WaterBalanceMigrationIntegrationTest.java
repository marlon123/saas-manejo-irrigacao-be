package com.irrigation.infrastructure.persistence;

import com.irrigation.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Migração V4 sobre dados preexistentes (Req 16.3, 16.4, 16.5, 1.11, 2.7, 3.4).
 *
 * <p>Cada teste usa um banco próprio no mesmo container: migra até a V3, insere dados,
 * migra até a V4 e confere que nada mudou nas colunas antigas.
 */
class WaterBalanceMigrationIntegrationTest extends PostgresIntegrationTest {

    private static final List<String> TABLES =
            List.of("areas", "crops", "crop_stages", "soil_profiles", "recommendations");

    /** Colunas que a V4 acrescenta; todas as demais devem permanecer idênticas. */
    private static final Map<String, List<String>> NEW_COLUMNS = Map.of(
            "soil_profiles", List.of("field_capacity_gravimetric_pct", "permanent_wilting_point_gravimetric_pct"),
            "crops", List.of("kc", "root_depth_mm"));

    private String url;
    private JdbcTemplate db;

    private final UUID areaId = UUID.randomUUID();
    private final UUID soilId = UUID.randomUUID();
    private final UUID cropId = UUID.randomUUID();

    @BeforeEach
    void createIsolatedDatabase() {
        String name = "v4_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + name);
        url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
        db = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private Flyway flyway(String target, String... locations) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(locations)
                .target(target)
                .load();
    }

    private void migrateTo(String target) {
        flyway(target, "classpath:db/migration").migrate();
    }

    /** Dados no formato da V3: áreas, solos, culturas com estágios e recomendações. */
    private void insertPreexistingData() {
        db.update("INSERT INTO areas (id, name, area_ha, latitude, longitude, general_characteristics, "
                + "irrigation_system_type, system_flow_rate, irrigation_efficiency) "
                + "VALUES (?, 'Talhão completo', 12.50, -23.5, -46.6, 'Argiloso', 'DRIP', 150.00, 0.9000)", areaId);
        db.update("INSERT INTO areas (id, name, area_ha, irrigation_system_type, system_flow_rate) "
                + "VALUES (?, 'Talhão mínimo', 3.00, 'FURROW', 15.00)", UUID.randomUUID());

        db.update("INSERT INTO soil_profiles (id, soil_type, field_capacity, permanent_wilting_point, bulk_density, "
                + "effective_root_depth, available_water, soil_water_unit, bulk_density_unit, root_depth_unit, "
                + "available_water_unit) VALUES (?, 'Argiloso', 0.3500, 0.1800, 1.350, 600.00, 102.00, "
                + "'VOLUMETRIC_WATER_CONTENT', 'GRAMS_PER_CUBIC_CENTIMETER', 'MILLIMETERS', 'MILLIMETERS')", soilId);
        db.update("INSERT INTO soil_profiles (id, soil_type, field_capacity, permanent_wilting_point, bulk_density, "
                + "effective_root_depth, soil_water_unit, bulk_density_unit, root_depth_unit, available_water_unit) "
                + "VALUES (?, 'Arenoso', 0.2000, 0.0800, 1.600, 400.00, 'VOLUMETRIC_WATER_CONTENT', "
                + "'GRAMS_PER_CUBIC_CENTIMETER', 'MILLIMETERS', 'MILLIMETERS')", UUID.randomUUID());

        db.update("INSERT INTO crops (id, name, variety, cultivation_cycle_days) VALUES (?, 'Milho', 'Híbrido', 120)",
                cropId);
        db.update("INSERT INTO crops (id, name) VALUES (?, 'Feijão')", UUID.randomUUID());
        db.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Inicial', 0.40, 1)", cropId);
        db.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Pleno', 1.20, 2)", cropId);

        db.update("INSERT INTO recommendations (area_id, date_time, input_data, results, methodology_id, "
                + "methodology_version) VALUES (?, '2026-01-10 08:00:00', '{\"a\": 1}'::jsonb, "
                + "'{\"b\": [1, 2]}'::jsonb, 'fake', '1.0')", areaId);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        for (String table : TABLES) {
            String order = table.equals("crop_stages") ? "stage_order, crop_id" : "id";
            result.put(table, db.queryForList("SELECT * FROM " + table + " ORDER BY " + order));
        }
        return result;
    }

    private List<Map<String, Object>> checksums() {
        return db.queryForList("SELECT version, checksum, description FROM flyway_schema_history "
                + "WHERE version IN ('1', '2', '3') ORDER BY installed_rank");
    }

    private void assertViolation(String sql, String constraint, Object... args) {
        assertThatThrownBy(() -> db.update(sql, args))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void migratingFromV3ToV4KeepsEveryRowAndEveryOldColumnValue() {
        migrateTo("3");
        insertPreexistingData();
        Map<String, List<Map<String, Object>>> before = snapshot();
        assertThat(before.get("areas")).hasSize(2);
        assertThat(before.get("soil_profiles")).hasSize(2);
        assertThat(before.get("crops")).hasSize(2);
        assertThat(before.get("crop_stages")).hasSize(2);
        assertThat(before.get("recommendations")).hasSize(1);

        migrateTo("4");
        Map<String, List<Map<String, Object>>> after = snapshot();

        for (String table : TABLES) {
            assertThat(after.get(table)).as("contagem de %s", table).hasSameSizeAs(before.get(table));
            List<String> added = NEW_COLUMNS.getOrDefault(table, List.of());
            List<Map<String, Object>> oldColumnsOnly = new ArrayList<>();
            for (Map<String, Object> row : after.get(table)) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
                added.forEach(copy::remove);
                oldColumnsOnly.add(copy);
            }
            assertThat(oldColumnsOnly).as("colunas antigas de %s", table).isEqualTo(before.get(table));
        }
    }

    @Test
    void newColumnsAreNullForPreexistingRows() {
        migrateTo("3");
        insertPreexistingData();
        migrateTo("4");

        assertThat(db.queryForObject("SELECT count(*) FROM soil_profiles WHERE field_capacity_gravimetric_pct IS NULL "
                + "AND permanent_wilting_point_gravimetric_pct IS NULL", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM crops WHERE kc IS NULL AND root_depth_mm IS NULL",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void checksumsOfV1ToV3AreUnchangedAndV4IsRecorded() {
        migrateTo("3");
        List<Map<String, Object>> before = checksums();
        assertThat(before).hasSize(3);
        assertThat(before).allSatisfy(row -> assertThat(row.get("checksum")).isNotNull());

        migrateTo("4");

        assertThat(checksums()).isEqualTo(before);
        List<Map<String, Object>> history = db.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void notNullConstraintsWereRelaxedOnlyWhereExpected() {
        migrateTo("4");

        List<String> nullable = db.queryForList("SELECT table_name || '.' || column_name FROM "
                + "information_schema.columns WHERE table_schema = 'public' AND is_nullable = 'YES' "
                + "AND table_name IN ('areas', 'soil_profiles')", String.class);
        assertThat(nullable).contains("areas.area_ha", "areas.irrigation_system_type", "areas.system_flow_rate",
                "soil_profiles.field_capacity", "soil_profiles.permanent_wilting_point",
                "soil_profiles.effective_root_depth", "soil_profiles.soil_water_unit",
                "soil_profiles.bulk_density_unit", "soil_profiles.root_depth_unit",
                "soil_profiles.available_water_unit");

        List<String> notNull = db.queryForList("SELECT table_name || '.' || column_name FROM "
                + "information_schema.columns WHERE table_schema = 'public' AND is_nullable = 'NO' "
                + "AND table_name IN ('areas', 'soil_profiles')", String.class);
        assertThat(notNull).contains("areas.name", "soil_profiles.soil_type", "soil_profiles.bulk_density")
                .doesNotContain("areas.area_ha", "soil_profiles.field_capacity");

        // Linhas com todos os campos opcionais nulos são aceitas
        db.update("INSERT INTO areas (name) VALUES ('Só nome')");
        db.update("INSERT INTO soil_profiles (soil_type, bulk_density) VALUES ('Só densidade', 1.300)");
        db.update("INSERT INTO crops (name) VALUES ('Sem kc')");
    }

    @Test
    void gravimetricChecksRejectInvalidValues() {
        migrateTo("4");
        String insert = "INSERT INTO soil_profiles (soil_type, bulk_density, field_capacity_gravimetric_pct, "
                + "permanent_wilting_point_gravimetric_pct) VALUES ('X', 1.300, ?::numeric, ?::numeric)";

        db.update(insert, 30.00, 15.00);
        db.update(insert, 100.00, 0.00);

        assertViolation(insert, "chk_fc_gravimetric_range", 100.01, 15.00);
        assertViolation(insert, "chk_fc_gravimetric_range", -0.01, null);
        // PMP acima de 100 sempre viola também CC > PMP; o limite inferior isola a regra de faixa
        assertViolation(insert, "chk_pwp_gravimetric_range", 10.00, -1.00);
        assertViolation(insert, "chk_gravimetric_pair", 30.00, null);
        assertViolation(insert, "chk_gravimetric_pair", null, 15.00);
        assertViolation(insert, "chk_fc_gt_pwp_gravimetric", 15.00, 15.00);
        assertViolation(insert, "chk_fc_gt_pwp_gravimetric", 10.00, 15.00);
    }

    @Test
    void volumetricChecksStillApplyAndThePairRuleIsEnforced() {
        migrateTo("4");
        String insert = "INSERT INTO soil_profiles (soil_type, bulk_density, field_capacity, "
                + "permanent_wilting_point) VALUES ('X', 1.300, ?::numeric, ?::numeric)";

        db.update(insert, 0.3500, 0.1800);

        assertViolation(insert, "chk_volumetric_pair", 0.3500, null);
        assertViolation(insert, "chk_volumetric_pair", null, 0.1800);
        assertViolation(insert, "chk_fc_gt_pwp", 0.1800, 0.1800);
        assertViolation(insert, "chk_fc_range", 1.5000, 0.1000);
        assertViolation("INSERT INTO soil_profiles (soil_type, bulk_density, effective_root_depth) "
                + "VALUES ('X', 1.300, ?)", "chk_root_depth_max", 5000.01);
    }

    @Test
    void cropChecksRejectKcAndRootDepthOutOfRange() {
        migrateTo("4");
        String insert = "INSERT INTO crops (name, kc, root_depth_mm) VALUES ('X', ?::numeric, ?::numeric)";

        db.update(insert, 2.00, 5000.00);
        db.update(insert, 0.01, 0.01);
        db.update(insert, new Object[] {null, null});

        assertViolation(insert, "chk_crop_kc_range", 0.00, 100.00);
        assertViolation(insert, "chk_crop_kc_range", 2.01, 100.00);
        assertViolation(insert, "chk_crop_kc_range", -0.50, 100.00);
        assertViolation(insert, "chk_crop_root_depth_mm_range", 1.00, 0.00);
        assertViolation(insert, "chk_crop_root_depth_mm_range", 1.00, 5000.01);
    }

    @Test
    void oldAreaChecksStillRejectNonPositiveValuesWhenPresent() {
        migrateTo("4");

        assertViolation("INSERT INTO areas (name, area_ha) VALUES ('X', ?)", "chk_area_ha_positive", 0.00);
        assertViolation("INSERT INTO areas (name, system_flow_rate) VALUES ('X', ?)", "chk_flow_rate_positive", 0.00);
    }

    /** A migração com erro não deixa alteração parcial: o DDL válido anterior ao erro é desfeito (Req 16.5). */
    @Test
    void aFailingMigrationOnTopOfV4LeavesNoPartialChange() {
        migrateTo("3");
        insertPreexistingData();
        migrateTo("4");
        Map<String, List<Map<String, Object>>> before = snapshot();

        Throwable failure = catchThrowable(
                () -> flyway("99", "classpath:db/migration", "classpath:db/failing-partial-migration").migrate());

        assertThat(failure).as("a migração deveria falhar").isNotNull();
        assertThat(failure.getMessage()).contains("99");
        assertThat(db.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'areas' AND column_name = 'should_not_persist'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '99' "
                + "AND success", Integer.class)).isZero();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success",
                Integer.class)).as("a V5 e a V6 são aplicadas antes da V99 e permanecem").isEqualTo(6);
    }
}
