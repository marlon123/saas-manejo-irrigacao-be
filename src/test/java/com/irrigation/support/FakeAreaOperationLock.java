package com.irrigation.support;

import com.irrigation.domain.repository.AreaOperationLock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@link AreaOperationLock} falso e configurável. Por padrão obtém o bloqueio; {@link #setAvailable(boolean)}
 * faz {@code tryLock} devolver {@code false}, simulando outra transação com o bloqueio. Registra as chamadas.
 */
public class FakeAreaOperationLock implements AreaOperationLock {

    private volatile boolean available = true;
    private final List<UUID> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean tryLock(UUID areaId) {
        calls.add(areaId);
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Áreas passadas a {@code tryLock}, em ordem de chamada. */
    public List<UUID> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public int callCount() {
        return calls.size();
    }

    public void reset() {
        calls.clear();
        available = true;
    }
}
