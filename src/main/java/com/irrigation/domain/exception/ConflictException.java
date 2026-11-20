package com.irrigation.domain.exception;

/**
 * Lançada quando há conflito de concorrência, como um cálculo ou reprocessamento já em
 * andamento para a mesma área. Mapeia para HTTP 409.
 */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
