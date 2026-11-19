package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.exception.BusinessRuleException;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduz a violação de FOREIGN KEY (SQLState 23503) ocorrida na exclusão em {@link BusinessRuleException} (422),
 * citando o tipo de referência conforme o nome da constraint {@code fk_*} (Req 1.13, 2.9, 3.5).
 */
final class ForeignKeyViolationTranslator {

    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final Pattern CONSTRAINT_IN_MESSAGE = Pattern.compile("\"(fk_[a-z0-9_]+)\"");

    private ForeignKeyViolationTranslator() {
    }

    /**
     * Retorna a exceção de regra de negócio equivalente, ou {@code null} se a causa não for uma violação de FOREIGN KEY.
     *
     * @param error       exceção capturada na exclusão
     * @param entityLabel rótulo da entidade excluída, por exemplo "a área"
     * @param id          identificador da entidade excluída
     */
    static BusinessRuleException translate(RuntimeException error, String entityLabel, UUID id) {
        boolean foreignKey = false;
        String constraint = null;
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConstraintViolationException hibernate) {
                if (FOREIGN_KEY_VIOLATION.equals(hibernate.getSQLState())) {
                    foreignKey = true;
                }
                if (constraint == null) {
                    constraint = hibernate.getConstraintName();
                }
            }
            if (cause instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
                foreignKey = true;
            }
            if (constraint == null && cause.getMessage() != null) {
                Matcher matcher = CONSTRAINT_IN_MESSAGE.matcher(cause.getMessage());
                if (matcher.find()) {
                    constraint = matcher.group(1);
                }
            }
        }
        if (!foreignKey) {
            return null;
        }
        return new BusinessRuleException(
                "Não é possível excluir " + entityLabel + " " + id + ": existem " + referenceType(constraint)
                        + " vinculados");
    }

    private static String referenceType(String constraint) {
        String name = constraint == null ? "" : constraint.toLowerCase();
        if (name.contains("management_plan")) {
            return "planos de manejo";
        }
        if (name.contains("daily_weather")) {
            return "dados meteorológicos diários";
        }
        if (name.contains("applied_irrigation")) {
            return "irrigações reais";
        }
        if (name.contains("water_balance")) {
            return "balanços hídricos";
        }
        if (name.contains("recommendation")) {
            return "recomendações de irrigação";
        }
        return "registros";
    }
}
