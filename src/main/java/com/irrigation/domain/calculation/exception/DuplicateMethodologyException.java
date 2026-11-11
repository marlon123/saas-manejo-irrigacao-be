package com.irrigation.domain.calculation.exception;

import com.irrigation.domain.exception.DomainException;
import com.irrigation.domain.calculation.MethodologyIdentifier;

/**
 * Lançada quando se tenta registrar uma metodologia com identificador já existente.
 * O registro existente é preservado inalterado. (Req 4.10) Mapeia para HTTP 422.
 */
public class DuplicateMethodologyException extends DomainException {

    private final String methodologyId;

    public DuplicateMethodologyException(MethodologyIdentifier methodologyId) {
        super(String.format("Metodologia já registrada com identificador: %s", methodologyId.value()));
        this.methodologyId = methodologyId.value();
    }

    public String getMethodologyId() {
        return methodologyId;
    }
}
