package com.irrigation.domain.calculation.exception;

import com.irrigation.domain.exception.DomainException;
import com.irrigation.domain.calculation.MethodologyIdentifier;

/**
 * Lançada quando uma metodologia de cálculo não registrada é solicitada.
 * (Req 4.6) Mapeia para HTTP 422.
 */
public class MethodologyNotAvailableException extends DomainException {

    private final String methodologyId;

    public MethodologyNotAvailableException(MethodologyIdentifier methodologyId) {
        super(String.format("Metodologia de cálculo não disponível: %s", methodologyId.value()));
        this.methodologyId = methodologyId.value();
    }

    public String getMethodologyId() {
        return methodologyId;
    }
}
