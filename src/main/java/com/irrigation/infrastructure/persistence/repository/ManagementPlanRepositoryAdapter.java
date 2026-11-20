package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.model.ManagementPlan;
import com.irrigation.domain.repository.ManagementPlanRepository;
import com.irrigation.infrastructure.persistence.mapper.ManagementPlanPersistenceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta de domínio {@link ManagementPlanRepository} com Spring Data JPA.
 */
@Repository
@Transactional
public class ManagementPlanRepositoryAdapter implements ManagementPlanRepository {

    /** Índice único parcial que garante no máximo um plano ativo (sem data final) por área. */
    static final String ACTIVE_PLAN_INDEX = "uq_management_plans_active_area";

    private final ManagementPlanJpaRepository jpa;
    private final ManagementPlanPersistenceMapper mapper;

    private final DailyWaterBalanceJpaRepository balanceJpa;

    public ManagementPlanRepositoryAdapter(ManagementPlanJpaRepository jpa, ManagementPlanPersistenceMapper mapper,
                                           DailyWaterBalanceJpaRepository balanceJpa) {
        this.jpa = jpa;
        this.balanceJpa = balanceJpa;
        this.mapper = mapper;
    }

    @Override
    public ManagementPlan save(ManagementPlan plan) {
        try {
            // saveAndFlush força o SQL agora, para a violação do índice surgir dentro deste método
            return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(plan)));
        } catch (DataIntegrityViolationException e) {
            if (violatesActivePlanIndex(e)) {
                throw new BusinessRuleException(
                        "A área já possui um plano de manejo ativo. Encerre o plano atual (informe a data final) "
                                + "antes de ativar outro.");
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagementPlan> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagementPlan> findByAreaOrderByStartDateDesc(UUID areaId) {
        return jpa.findByAreaIdOrderByStartDateDesc(areaId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagementPlan> findActiveByArea(UUID areaId) {
        return jpa.findByAreaIdAndEndDateIsNull(areaId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagementPlan> findCoveringDate(UUID areaId, LocalDate date) {
        // a consulta ordena por início decrescente: o primeiro item é o plano mais recente que cobre a data
        return jpa.findCoveringDate(areaId, date).stream().findFirst().map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBalances(UUID planId) {
        return balanceJpa.existsByManagementPlanId(planId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastCalculatedDate(UUID planId) {
        return Optional.ofNullable(balanceJpa.findLastCurrentBalanceDate(planId));
    }

    private static boolean violatesActivePlanIndex(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && ACTIVE_PLAN_INDEX.equals(cve.getConstraintName())) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.contains(ACTIVE_PLAN_INDEX)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}