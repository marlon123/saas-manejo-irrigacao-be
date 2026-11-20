package com.irrigation.domain.exception;

/**
 * Lançada na tentativa de alterar ou excluir um balanço hídrico já gravado (Req 12.8).
 * Mapeia para HTTP 422.
 */
public class ImmutableRecordException extends BusinessRuleException {

    public static final String DEFAULT_MESSAGE =
            "Registro imutável: o balanço hídrico gravado não pode ser alterado nem excluído";

    public ImmutableRecordException() {
        super(DEFAULT_MESSAGE);
    }

    public ImmutableRecordException(Throwable cause) {
        this();
        initCause(cause);
    }
}
