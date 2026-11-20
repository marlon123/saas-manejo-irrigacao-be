package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.exception.BusinessRuleException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link ForeignKeyViolationTranslator} com exceções simuladas (Req 1.13, 2.9, 3.5, 16.6).
 */
class ForeignKeyViolationTranslatorTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static DataIntegrityViolationException hibernateFk(String constraint, String sqlState) {
        SQLException sql = new SQLException("violação", sqlState);
        return new DataIntegrityViolationException("erro",
                new ConstraintViolationException("violação", sql, constraint));
    }

    private static String translate(RuntimeException error) {
        BusinessRuleException result = ForeignKeyViolationTranslator.translate(error, "a área", ID);
        assertThat(result).isNotNull();
        return result.getMessage();
    }

    @Test
    void mapsEachConstraintNameToItsReferenceType() {
        assertThat(translate(hibernateFk("fk_management_plans_area", "23503"))).contains("planos de manejo");
        assertThat(translate(hibernateFk("fk_daily_weather_area", "23503"))).contains("dados meteorológicos diários");
        assertThat(translate(hibernateFk("fk_applied_irrigation_area", "23503"))).contains("irrigações reais");
        assertThat(translate(hibernateFk("fk_daily_water_balance_area", "23503"))).contains("balanços hídricos");
        assertThat(translate(hibernateFk("fk_recommendations_area", "23503"))).contains("recomendações de irrigação");
        assertThat(translate(hibernateFk("fk_other", "23503"))).contains("registros");
    }

    @Test
    void messageIncludesEntityLabelAndId() {
        assertThat(translate(hibernateFk("fk_recommendations_area", "23503")))
                .contains("a área").contains(ID.toString());
    }

    @Test
    void usesUppercaseConstraintNameCaseInsensitively() {
        assertThat(translate(hibernateFk("FK_MANAGEMENT_PLANS_AREA", "23503"))).contains("planos de manejo");
    }

    @Test
    void translatesPlainSqlExceptionAndReadsConstraintFromMessage() {
        SQLException sql = new SQLException(
                "ERROR: update or delete on table \"areas\" violates foreign key constraint "
                        + "\"fk_applied_irrigation_area\" on table \"applied_irrigation\"", "23503");
        assertThat(translate(new DataIntegrityViolationException("erro", sql))).contains("irrigações reais");
    }

    @Test
    void fallsBackToGenericReferenceWhenConstraintIsUnknown() {
        SQLException sql = new SQLException("violação sem nome", "23503");
        assertThat(translate(new DataIntegrityViolationException("erro", sql))).contains("registros");
    }

    @Test
    void returnsNullWhenSqlStateIsNotForeignKey() {
        assertThat(ForeignKeyViolationTranslator.translate(
                hibernateFk("uq_areas_name", "23505"), "a área", ID)).isNull();
        assertThat(ForeignKeyViolationTranslator.translate(
                new DataIntegrityViolationException("erro", new SQLException("x", "23505")), "a área", ID)).isNull();
    }

    @Test
    void returnsNullWhenThereIsNoSqlCause() {
        assertThat(ForeignKeyViolationTranslator.translate(
                new IllegalStateException("sem causa"), "a área", ID)).isNull();
        assertThat(ForeignKeyViolationTranslator.translate(
                new IllegalStateException(), "a área", ID)).isNull();
    }
}
