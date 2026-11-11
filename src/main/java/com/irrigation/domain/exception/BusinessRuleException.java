package com.irrigation.domain.exception;

/**
 * Lançada quando uma regra de negócio é violada. Mapeia para HTTP 422.
 * Ex.: tentar excluir uma área referenciada por recomendações (Req 1.19).
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
