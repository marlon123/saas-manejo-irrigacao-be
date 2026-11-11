package com.irrigation.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração (Task 24): aplicação completa sobre um PostgreSQL real.
 *
 * <p>O container é um singleton da JVM: iniciado uma única vez e removido pelo Ryuk ao fim.
 * Assim o contexto do Spring, que fica em cache entre as classes de teste, sempre aponta
 * para um banco no ar. O {@code @ServiceConnection} configura o datasource a partir dele.
 *
 * <p>Os testes com a tag {@code integration} ficam fora da tarefa {@code test} e rodam com
 * {@code gradlew integrationTest} (exigem Docker).
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresIntegrationTest.FakeMethodologyConfig.class)
public abstract class PostgresIntegrationTest {

    /** Metodologia falsa registrada no motor, só para permitir o cálculo nos testes. */
    public static final String FAKE_METHODOLOGY = "fake";

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /** Cada teste começa com as tabelas de negócio vazias (o histórico do Flyway é preservado). */
    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE recommendations, crop_stages, crops, soil_profiles, areas CASCADE");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeMethodologyConfig {

        /** Não implementa fórmula agronômica; devolve um resultado fixo (ver FakeCalculationMethod). */
        @Bean
        FakeCalculationMethod fakeCalculationMethod() {
            return new FakeCalculationMethod(FAKE_METHODOLOGY);
        }
    }
}
