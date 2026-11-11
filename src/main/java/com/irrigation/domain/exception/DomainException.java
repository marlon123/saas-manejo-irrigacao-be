package com.irrigation.domain.exception;

/**
 * Exceção base do domínio. Todas as exceções de negócio herdam desta classe.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
