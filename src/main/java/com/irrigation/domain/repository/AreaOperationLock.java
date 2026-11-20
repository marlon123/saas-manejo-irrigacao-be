package com.irrigation.domain.repository;

import java.util.UUID;

/**
 * Porta de bloqueio exclusivo por área, com escopo de transação.
 * Serializa operações de escrita do balanço hídrico na mesma área.
 */
public interface AreaOperationLock {

    /**
     * Tenta obter o bloqueio exclusivo da área, sem esperar.
     * O bloqueio é liberado ao fim da transação corrente.
     *
     * @return {@code true} se obteve o bloqueio; {@code false} se outra transação o detém
     */
    boolean tryLock(UUID areaId);
}
