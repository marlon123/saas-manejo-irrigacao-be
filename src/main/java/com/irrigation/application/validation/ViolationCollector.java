package com.irrigation.application.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Acumula violações de validação por campo, em vez de falhar na primeira.
 *
 * <p>As regras continuam vivendo nos value objects e agregados do domínio
 * (que lançam {@link IllegalArgumentException}); este coletor apenas traduz
 * cada falha em uma {@link FieldViolation} identificando o campo.
 */
public final class ViolationCollector {

    private static final String REQUIRED = "é obrigatório";

    private final List<FieldViolation> violations = new ArrayList<>();

    public void add(String field, String message) {
        violations.add(new FieldViolation(field, message));
    }

    /** Registra violação se o valor for nulo; retorna o próprio valor. */
    public <T> T require(String field, T value) {
        if (value == null) {
            add(field, REQUIRED);
        }
        return value;
    }

    /** Constrói um objeto a partir de um valor obrigatório, registrando a violação em caso de falha. */
    public <R, T> T build(String field, R raw, Function<R, T> factory) {
        if (raw == null) {
            add(field, REQUIRED);
            return null;
        }
        return attempt(field, () -> factory.apply(raw));
    }

    /** Como {@link #build}, mas o valor é opcional: nulo resulta em nulo, sem violação. */
    public <R, T> T buildOptional(String field, R raw, Function<R, T> factory) {
        if (raw == null) {
            return null;
        }
        return attempt(field, () -> factory.apply(raw));
    }

    /** Executa a ação; se o domínio rejeitar com {@link IllegalArgumentException}, registra a violação. */
    public <T> T attempt(String field, Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            add(field, e.getMessage());
            return null;
        }
    }

    /** Executa uma validação sem retorno; se o domínio rejeitar com {@link IllegalArgumentException}, registra a violação. */
    public boolean check(String field, Runnable validation) {
        try {
            validation.run();
            return true;
        } catch (IllegalArgumentException e) {
            add(field, e.getMessage());
            return false;
        }
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public void throwIfAny() {
        if (hasViolations()) {
            throw new ValidationFailedException(violations);
        }
    }
}
