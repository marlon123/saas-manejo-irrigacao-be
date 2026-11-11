package com.irrigation.application.validation;

/**
 * Violação de validação associada a um campo da requisição (Req 7.1).
 *
 * @param field   nome do campo (ex.: {@code areaHectares}, {@code stages[0].kc})
 * @param message descrição do erro
 */
public record FieldViolation(String field, String message) {
}
