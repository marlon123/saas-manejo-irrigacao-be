package com.irrigation.infrastructure.health;

import com.irrigation.application.health.DatabaseHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Verifica a conectividade abrindo uma conexão JDBC e validando-a (timeout de 2 s).
 */
@Component
public class JdbcDatabaseHealth implements DatabaseHealth {

    private static final Logger log = LoggerFactory.getLogger(JdbcDatabaseHealth.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public JdbcDatabaseHealth(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Banco de dados inacessível: {}", e.getMessage());
            return false;
        }
    }
}
