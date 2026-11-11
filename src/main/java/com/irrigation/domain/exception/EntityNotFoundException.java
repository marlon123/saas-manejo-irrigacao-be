package com.irrigation.domain.exception;

/**
 * Lançada quando um recurso não é encontrado. Mapeia para HTTP 404.
 */
public class EntityNotFoundException extends DomainException {

    private final String entityType;
    private final Object identifier;

    public EntityNotFoundException(String entityType, Object identifier) {
        super(String.format("%s não encontrado(a) com identificador: %s", entityType, identifier));
        this.entityType = entityType;
        this.identifier = identifier;
    }

    public String getEntityType() {
        return entityType;
    }

    public Object getIdentifier() {
        return identifier;
    }
}
