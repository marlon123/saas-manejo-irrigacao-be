package com.irrigation.support;

import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.repository.ManagementPlanRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de planos de manejo em memória.
 *
 * <p>Na Fase 3 não existe tabela de balanços. Para simular planos com balanço calculado, os testes usam
 * {@link #registerBalance(UUID, LocalDate)}: {@code hasBalances} e {@code lastCalculatedDate} passam a
 * refletir os balanços fictícios registrados.
 */
public class InMemoryManagementPlanRepository implements ManagementPlanRepository {

    private final Map<UUID, ManagementPlan> store = new LinkedHashMap<>();
    private final Map<UUID, LocalDate> lastBalanceDates = new HashMap<>();

    @Override
    public ManagementPlan save(ManagementPlan plan) {
        store.put(plan.getId(), plan);
        return plan;
    }

    @Override
    public Optional<ManagementPlan> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ManagementPlan> findByAreaOrderByStartDateDesc(UUID areaId) {
        return store.values().stream()
                .filter(p -> p.getAreaId().equals(areaId))
                .sorted(Comparator.comparing(ManagementPlan::getStartDate).reversed())
                .toList();
    }

    @Override
    public Optional<ManagementPlan> findActiveByArea(UUID areaId) {
        return store.values().stream()
                .filter(p -> p.getAreaId().equals(areaId) && p.isActive())
                .findFirst();
    }

    @Override
    public Optional<ManagementPlan> findCoveringDate(UUID areaId, LocalDate date) {
        return store.values().stream()
                .filter(p -> p.getAreaId().equals(areaId))
                .filter(p -> !p.getStartDate().isAfter(date)
                        && (p.getEndDate() == null || !p.getEndDate().isBefore(date)))
                .findFirst();
    }

    @Override
    public boolean hasBalances(UUID planId) {
        return lastBalanceDates.containsKey(planId);
    }

    @Override
    public Optional<LocalDate> lastCalculatedDate(UUID planId) {
        return Optional.ofNullable(lastBalanceDates.get(planId));
    }

    /** Registra um balanço fictício do plano na data; mantém a maior data como a última calculada. */
    public void registerBalance(UUID planId, LocalDate date) {
        lastBalanceDates.merge(planId, date, (a, b) -> a.isAfter(b) ? a : b);
    }

    /** Remove os balanços fictícios do plano. */
    public void clearBalances(UUID planId) {
        lastBalanceDates.remove(planId);
    }

    public int count() {
        return store.size();
    }
}
