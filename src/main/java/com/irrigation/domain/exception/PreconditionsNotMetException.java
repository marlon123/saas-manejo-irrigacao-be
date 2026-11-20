package com.irrigation.domain.exception;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Lançada quando o cálculo ou o reprocessamento do balanço hídrico de uma data não pode
 * prosseguir por falta de dados (Req 11). Mapeia para HTTP 422. Reúne todas as
 * pré-condições não atendidas da data, sem repetições e na ordem em que foram informadas.
 */
public class PreconditionsNotMetException extends BusinessRuleException {

    private final LocalDate date;
    private final List<Precondition> preconditions;

    public PreconditionsNotMetException(LocalDate date, Collection<Precondition> preconditions) {
        super(buildMessage(date, preconditions));
        this.date = date;
        this.preconditions = List.copyOf(new LinkedHashSet<>(preconditions));
    }

    private static String buildMessage(LocalDate date, Collection<Precondition> preconditions) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(preconditions, "preconditions");
        if (preconditions.isEmpty()) {
            throw new IllegalArgumentException("preconditions must not be empty");
        }
        return "Pré-condições não atendidas para a data " + date;
    }

    public LocalDate getDate() {
        return date;
    }

    /** Lista imutável, sem duplicatas. */
    public List<Precondition> getPreconditions() {
        return preconditions;
    }

    /** Códigos das pré-condições (Req 11). */
    public enum Code {
        WEATHER_MISSING,
        APPLIED_IRRIGATION_MISSING,
        SOIL_FIELDS_MISSING,
        CROP_FIELDS_MISSING
    }

    /**
     * Uma pré-condição não atendida.
     *
     * @param code    código da falha
     * @param message descrição legível
     * @param fields  campos ausentes (pode ser vazia; imutável)
     */
    public record Precondition(Code code, String message, List<String> fields) {

        public Precondition {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        public Precondition(Code code, String message) {
            this(code, message, List.of());
        }
    }
}
