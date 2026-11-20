package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.repository.AreaOperationLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueio exclusivo por área baseado em {@code pg_try_advisory_xact_lock(int, int)}.
 *
 * <p>A chave tem duas partes: um inteiro fixo do módulo de balanço hídrico e o hash estável do
 * UUID da área. O bloqueio tem escopo de transação e é liberado no commit ou rollback, por isso
 * o método exige uma transação ativa.
 */
@Repository
public class PostgresAreaOperationLock implements AreaOperationLock {

    /** Primeira parte da chave: identifica o módulo de balanço hídrico. */
    static final int MODULE_KEY = 0x57_42_41_4C; // "WBAL"

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryLock(UUID areaId) {
        Objects.requireNonNull(areaId, "areaId");
        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:moduleKey, :areaKey)")
                .setParameter("moduleKey", MODULE_KEY)
                .setParameter("areaKey", areaKey(areaId))
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    /** Hash estável (independe da JVM) dos 128 bits do UUID, reduzido a 32 bits. */
    static int areaKey(UUID areaId) {
        long bits = areaId.getMostSignificantBits() ^ areaId.getLeastSignificantBits();
        return (int) (bits >>> 32) ^ (int) bits;
    }
}
