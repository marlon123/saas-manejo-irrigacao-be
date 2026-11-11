package com.irrigation.infrastructure.persistence;

import com.irrigation.IrrigationApplication;
import com.irrigation.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Migrações Flyway no PostgreSQL real (Req 8.1, 8.3, 8.5).
 *
 * <p>Subir o contexto da classe base já prova que V1 a V3 aplicam e que o mapeamento JPA
 * bate com o schema ({@code ddl-auto: validate}); aqui conferimos o resultado em detalhe.
 */
class MigrationIntegrationTest extends PostgresIntegrationTest {

    @Test
    void migrationsWereAppliedSuccessfullyInAscendingVersionOrder() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(row -> row.get("version")).containsExactly("1", "2", "3");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("areas", "crops", "crop_stages", "soil_profiles", "recommendations");
    }

    @Test
    void v2AddedUnitColumnsToSoilProfiles() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'soil_profiles' "
                        + "AND is_nullable = 'NO'", String.class);

        assertThat(columns).contains("soil_water_unit", "bulk_density_unit", "root_depth_unit", "available_water_unit");
    }

    @Test
    void v2MadeTheStageOrderUniquenessDeferrable() {
        Map<String, Object> constraint = jdbc.queryForMap(
                "SELECT contype, condeferrable, condeferred FROM pg_constraint WHERE conname = 'uq_crop_stage_order'");

        assertThat(constraint.get("contype").toString()).isEqualTo("u");
        assertThat(constraint.get("condeferrable")).isEqualTo(true);
        assertThat(constraint.get("condeferred")).isEqualTo(true);
    }

    @Test
    void recommendationSnapshotColumnsAreJsonb() {
        List<String> types = jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'recommendations' "
                        + "AND column_name IN ('input_data', 'results')", String.class);

        assertThat(types).containsExactly("jsonb", "jsonb");
    }

    /**
     * Uma migração com erro interrompe a inicialização e a falha identifica a versão (Req 8.5).
     * Usa um banco próprio no mesmo container, para não afetar o schema dos demais testes.
     */
    @Test
    void aFailingMigrationAbortsStartupAndReportsTheFailedVersion() {
        jdbc.execute("DROP DATABASE IF EXISTS failing_migration");
        jdbc.execute("CREATE DATABASE failing_migration");
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/failing_migration");

        Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(IrrigationApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.flyway.locations=classpath:db/migration,classpath:db/failing-migration")
                .close());

        assertThat(failure).as("a inicialização deveria falhar").isNotNull();
        assertThat(messagesOf(failure)).contains("V99__broken_migration.sql");

        // As migrações válidas continuam aplicadas; a V99 não foi registrada como sucesso
        List<Map<String, Object>> history = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()))
                .queryForList("SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL "
                        + "ORDER BY installed_rank");
        assertThat(history).filteredOn(row -> Boolean.TRUE.equals(row.get("success")))
                .extracting(row -> row.get("version")).containsExactly("1", "2", "3");
    }

    /** Junta as mensagens de toda a cadeia de causas. */
    private static String messagesOf(Throwable t) {
        List<String> messages = new ArrayList<>();
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            messages.add(String.valueOf(cause.getMessage()));
        }
        return String.join("\n", messages);
    }
}
