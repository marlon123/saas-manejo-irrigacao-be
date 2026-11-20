package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.ImmutableRecordException;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.infrastructure.persistence.mapper.DailyWaterBalancePersistenceMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa a porta {@link DailyWaterBalanceRepository} com Spring Data JPA.
 *
 * <p>Traduz erros do banco: violação do índice de balanço corrente ou da unicidade por revisão vira
 * {@link ConflictException}; erro do trigger de imutabilidade (SQLState 23000, mensagem
 * {@code immutable_daily_water_balance}) vira {@link ImmutableRecordException}.
 */
@Repository
@Transactional
public class DailyWaterBalanceRepositoryAdapter implements DailyWaterBalanceRepository {

    /** Índice único parcial: no máximo um balanço corrente por área e data. */
    static final String CURRENT_INDEX = "uq_daily_water_balance_current";
    /** Unicidade por (área, data, revisão). */
    static final String REVISION_CONSTRAINT = "uq_daily_water_balance_revision";
    /** Texto da mensagem levantada pelos triggers de imutabilidade (V6). */
    static final String IMMUTABLE_MARKER = "immutable_daily_water_balance";
    /** ERRCODE usado pelos triggers de imutabilidade (V6). */
    static final String IMMUTABLE_SQL_STATE = "23000";

    private static final Sort DATE_ASC = Sort.by(Sort.Direction.ASC, "balanceDate");

    private final DailyWaterBalanceJpaRepository jpa;
    private final DailyWaterBalancePersistenceMapper mapper;

    public DailyWaterBalanceRepositoryAdapter(DailyWaterBalanceJpaRepository jpa,
                                              DailyWaterBalancePersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DailyWaterBalance insert(DailyWaterBalance balance) {
        try {
            // saveAndFlush força o SQL agora, para a violação surgir dentro deste método
            return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(balance)));
        } catch (DataIntegrityViolationException e) {
            throw translate(e, balance);
        }
    }

    @Override
    public List<DailyWaterBalance> insertAll(List<DailyWaterBalance> balances) {
        List<DailyWaterBalance> saved = new ArrayList<>(balances.size());
        for (DailyWaterBalance balance : balances) {
            saved.add(insert(balance));
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DailyWaterBalance> findCurrent(UUID areaId, LocalDate date) {
        return jpa.findByAreaIdAndBalanceDateAndCurrentTrue(areaId, date).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyWaterBalance> findCurrentFrom(UUID areaId, LocalDate date) {
        return jpa.findByAreaIdAndBalanceDateGreaterThanEqualAndCurrentTrueOrderByBalanceDateAsc(areaId, date)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyWaterBalance> findCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to,
                                                       PageRequest pageRequest) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), DATE_ASC);
        return jpa.findByAreaIdAndBalanceDateBetweenAndCurrentTrue(areaId, lowerBound(from), upperBound(to), pageable)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return jpa.countByAreaIdAndBalanceDateBetweenAndCurrentTrue(areaId, lowerBound(from), upperBound(to));
    }

    /** Limites abertos (from ou to nulos) viram datas extremas, pois BETWEEN não casa valores nulos. */
    private static LocalDate lowerBound(LocalDate from) {
        return from != null ? from : LocalDate.of(1, 1, 1);
    }

    private static LocalDate upperBound(LocalDate to) {
        return to != null ? to : LocalDate.of(9999, 12, 31);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyWaterBalance> findRevisions(UUID areaId, LocalDate date) {
        return jpa.findByAreaIdAndBalanceDateOrderByRevisionDesc(areaId, date)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public int markNotCurrent(UUID areaId, LocalDate fromDate) {
        try {
            return jpa.markNotCurrent(areaId, fromDate);
        } catch (DataIntegrityViolationException e) {
            if (isImmutabilityError(e)) {
                throw new ImmutableRecordException(e);
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> firstMissingDate(UUID areaId, LocalDate fromDate, LocalDate toDate) {
        return jpa.firstMissingDate(areaId, fromDate, toDate);
    }

    private RuntimeException translate(DataIntegrityViolationException error, DailyWaterBalance balance) {
        if (isImmutabilityError(error)) {
            return new ImmutableRecordException(error);
        }
        if (violates(error, CURRENT_INDEX) || violates(error, REVISION_CONSTRAINT)) {
            return new ConflictException("Já existe balanço hídrico corrente para a área " + balance.areaId()
                    + " na data " + balance.date() + " (revisão " + balance.revision()
                    + "). Outro cálculo pode estar em andamento; tente novamente.");
        }
        return error;
    }

    private static boolean isImmutabilityError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(IMMUTABLE_MARKER)) {
                return true;
            }
            if (t instanceof SQLException sql && IMMUTABLE_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static boolean violates(Throwable error, String constraint) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConstraintViolationException cve && constraint.equals(cve.getConstraintName())) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
