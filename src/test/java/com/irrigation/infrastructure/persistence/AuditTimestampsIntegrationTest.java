package com.irrigation.infrastructure.persistence;

import com.irrigation.domain.model.Area;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.SoilProfileRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Colunas de auditoria mantidas pelo banco (V3): defaults em UTC e trigger de updated_at.
 */
class AuditTimestampsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    AreaRepository areas;
    @Autowired
    CropRepository crops;
    @Autowired
    SoilProfileRepository soilProfiles;

    private LocalDateTime column(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", LocalDateTime.class, id);
    }

    /** Garante que a próxima transação tenha um now() diferente da anterior. */
    private void tick() throws InterruptedException {
        Thread.sleep(20);
    }

    @Test
    void insertFillsBothColumnsWithTheSameUtcInstant() {
        UUID id = areas.save(TestData.area()).getId();

        LocalDateTime created = column("areas", "created_at", id);
        assertThat(column("areas", "updated_at", id)).isEqualTo(created);
        assertThat(created).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }

    /**
     * Mesmo com a sessão em outro fuso, o default grava UTC. Sem a V3, now() seria
     * convertido para o fuso da sessão (aqui, UTC-3).
     */
    @Test
    void defaultsAreUtcRegardlessOfTheSessionTimeZone() {
        LocalDateTime created = jdbc.execute((ConnectionCallback<LocalDateTime>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO areas (id, name, area_ha, irrigation_system_type, system_flow_rate) "
                                 + "VALUES (?, 'Fuso', 1, 'DRIP', 1) RETURNING created_at")) {
                statement.execute("SET LOCAL TIME ZONE 'America/Sao_Paulo'");
                insert.setObject(1, UUID.randomUUID());
                try (ResultSet rs = insert.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, LocalDateTime.class);
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        });

        assertThat(created).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void updatingAnAreaRefreshesUpdatedAtAndKeepsCreatedAt() throws Exception {
        Area area = areas.save(TestData.area());
        LocalDateTime created = column("areas", "created_at", area.getId());
        tick();

        areas.save(TestData.area(area.getId(), "Nome novo", "10.50"));

        assertThat(column("areas", "created_at", area.getId())).isEqualTo(created);
        assertThat(column("areas", "updated_at", area.getId())).isAfter(created);
    }

    /** Salvar sem nenhuma alteração não conta como atualização. */
    @Test
    void savingAnUnchangedAreaKeepsUpdatedAt() throws Exception {
        Area area = areas.save(TestData.area());
        LocalDateTime updated = column("areas", "updated_at", area.getId());
        tick();

        areas.save(area);

        assertThat(column("areas", "updated_at", area.getId())).isEqualTo(updated);
    }

    @ParameterizedTest
    @CsvSource({"areas, name", "crops, name", "soil_profiles, soil_type"})
    void everyAuditedTableHasTheTrigger(String table, String textColumn) throws Exception {
        UUID id = switch (table) {
            case "areas" -> areas.save(TestData.area()).getId();
            case "crops" -> crops.save(TestData.crop()).getId();
            default -> soilProfiles.save(TestData.soilProfile()).getId();
        };
        LocalDateTime created = column(table, "created_at", id);
        tick();

        jdbc.update("UPDATE " + table + " SET " + textColumn + " = 'alterado' WHERE id = ?", id);

        assertThat(column(table, "updated_at", id)).isAfter(created);
    }
}
