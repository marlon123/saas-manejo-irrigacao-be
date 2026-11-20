package com.irrigation.support;

import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Repositório de balanços hídricos diários em memória.
 *
 * <p>Modela as restrições da tabela real:
 * <ul>
 *   <li>índice único parcial: no máximo um balanço corrente por área e data;</li>
 *   <li>unicidade por revisão: não repete (área, data, revisão).</li>
 * </ul>
 * Violações lançam {@link ConflictException}.
 *
 * <p><b>Falha injetada (Propriedade 21).</b> {@link #failOnInsertNumber(int)} faz o N-ésimo insert (contado a
 * partir da chamada, 1 = primeiro) lançar {@link InjectedFailureException} antes de gravar; {@link #failOnMarkNotCurrent()}
 * faz o próximo {@code markNotCurrent} falhar. {@link #insertAll(List)} e {@link #markNotCurrent(UUID, LocalDate)}
 * desfazem tudo o que gravaram na própria chamada ao falhar. Para desfazer uma sequência (por exemplo
 * {@code markNotCurrent} seguido de {@code insertAll}, como o reprocessamento), use {@link #atomically(Supplier)},
 * que restaura o estado anterior a qualquer {@link RuntimeException}, como a transação do banco.
 */
public class InMemoryDailyWaterBalanceRepository implements DailyWaterBalanceRepository {

    /** Falha simulada de gravação. */
    public static class InjectedFailureException extends RuntimeException {
        public InjectedFailureException(String message) {
            super(message);
        }
    }

    private Map<UUID, DailyWaterBalance> store = new LinkedHashMap<>();

    private int failAtInsertNumber = -1;
    private int insertsSinceArmed;
    private boolean failOnMarkNotCurrent;

    // ------------------------------------------------------------ Falha injetada

    /** O N-ésimo insert a partir de agora (N ≥ 1) falha; os anteriores gravam normalmente. */
    public synchronized void failOnInsertNumber(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n deve ser maior ou igual a 1");
        }
        this.failAtInsertNumber = n;
        this.insertsSinceArmed = 0;
    }

    /** O próximo {@code markNotCurrent} falha. */
    public synchronized void failOnMarkNotCurrent() {
        this.failOnMarkNotCurrent = true;
    }

    /** Remove qualquer falha injetada. */
    public synchronized void clearInjectedFailures() {
        this.failAtInsertNumber = -1;
        this.insertsSinceArmed = 0;
        this.failOnMarkNotCurrent = false;
    }

    // ------------------------------------------------------------ Transação simulada

    /** Executa a ação e restaura o estado anterior se ela lançar {@link RuntimeException}. */
    public synchronized <T> T atomically(Supplier<T> action) {
        Map<UUID, DailyWaterBalance> snapshot = new LinkedHashMap<>(store);
        try {
            return action.get();
        } catch (RuntimeException e) {
            store = snapshot;
            throw e;
        }
    }

    /** Cópia do estado atual, para comparar antes e depois de uma operação que falha. */
    public synchronized List<DailyWaterBalance> snapshot() {
        return new ArrayList<>(store.values());
    }

    // ------------------------------------------------------------ Porta

    @Override
    public synchronized DailyWaterBalance insert(DailyWaterBalance balance) {
        Map<UUID, DailyWaterBalance> snapshot = new LinkedHashMap<>(store);
        try {
            return doInsert(balance);
        } catch (RuntimeException e) {
            store = snapshot;
            throw e;
        }
    }

    @Override
    public synchronized List<DailyWaterBalance> insertAll(List<DailyWaterBalance> balances) {
        Map<UUID, DailyWaterBalance> snapshot = new LinkedHashMap<>(store);
        try {
            List<DailyWaterBalance> saved = new ArrayList<>();
            for (DailyWaterBalance balance : balances) {
                saved.add(doInsert(balance));
            }
            return saved;
        } catch (RuntimeException e) {
            store = snapshot;
            throw e;
        }
    }

    private DailyWaterBalance doInsert(DailyWaterBalance balance) {
        if (failAtInsertNumber > 0) {
            insertsSinceArmed++;
            if (insertsSinceArmed == failAtInsertNumber) {
                failAtInsertNumber = -1;
                throw new InjectedFailureException("falha injetada no insert " + insertsSinceArmed);
            }
        }
        for (DailyWaterBalance existing : store.values()) {
            if (!existing.areaId().equals(balance.areaId()) || !existing.date().equals(balance.date())) {
                continue;
            }
            if (existing.revision() == balance.revision()) {
                throw new ConflictException("Já existe a revisão " + balance.revision()
                        + " do balanço da área na data " + balance.date());
            }
            if (existing.current() && balance.current()) {
                throw new ConflictException("Já existe balanço corrente da área na data " + balance.date());
            }
        }
        if (store.containsKey(balance.id())) {
            throw new ConflictException("Já existe balanço com o id " + balance.id());
        }
        store.put(balance.id(), balance);
        return balance;
    }

    @Override
    public synchronized Optional<DailyWaterBalance> findCurrent(UUID areaId, LocalDate date) {
        return store.values().stream()
                .filter(b -> b.areaId().equals(areaId) && b.date().equals(date) && b.current())
                .findFirst();
    }

    @Override
    public synchronized List<DailyWaterBalance> findCurrentFrom(UUID areaId, LocalDate date) {
        return store.values().stream()
                .filter(b -> b.areaId().equals(areaId) && b.current() && !b.date().isBefore(date))
                .sorted(Comparator.comparing(DailyWaterBalance::date))
                .toList();
    }

    @Override
    public synchronized List<DailyWaterBalance> findCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to,
                                                                    PageRequest pageRequest) {
        return currentInPeriod(areaId, from, to)
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
    }

    @Override
    public synchronized long countCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return currentInPeriod(areaId, from, to).count();
    }

    private java.util.stream.Stream<DailyWaterBalance> currentInPeriod(UUID areaId, LocalDate from, LocalDate to) {
        return store.values().stream()
                .filter(b -> b.areaId().equals(areaId) && b.current())
                .filter(b -> (from == null || !b.date().isBefore(from)) && (to == null || !b.date().isAfter(to)))
                .sorted(Comparator.comparing(DailyWaterBalance::date));
    }

    @Override
    public synchronized List<DailyWaterBalance> findRevisions(UUID areaId, LocalDate date) {
        return store.values().stream()
                .filter(b -> b.areaId().equals(areaId) && b.date().equals(date))
                .sorted(Comparator.comparingInt(DailyWaterBalance::revision).reversed())
                .toList();
    }

    @Override
    public synchronized int markNotCurrent(UUID areaId, LocalDate fromDate) {
        if (failOnMarkNotCurrent) {
            failOnMarkNotCurrent = false;
            throw new InjectedFailureException("falha injetada em markNotCurrent");
        }
        int changed = 0;
        for (Map.Entry<UUID, DailyWaterBalance> entry : store.entrySet()) {
            DailyWaterBalance b = entry.getValue();
            if (b.areaId().equals(areaId) && b.current() && !b.date().isBefore(fromDate)) {
                entry.setValue(b.markNotCurrent());
                changed++;
            }
        }
        return changed;
    }

    @Override
    public synchronized Optional<LocalDate> firstMissingDate(UUID areaId, LocalDate fromDate, LocalDate toDate) {
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            if (findCurrent(areaId, d).isEmpty()) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------ Apoio aos testes

    /** Todos os registros (correntes e antigos) em ordem de gravação. */
    public synchronized List<DailyWaterBalance> findAll() {
        return new ArrayList<>(store.values());
    }

    /** Registros do plano, correntes e antigos. */
    public synchronized List<DailyWaterBalance> findByPlan(UUID planId) {
        return store.values().stream().filter(b -> b.managementPlanId().equals(planId)).toList();
    }

    public synchronized int count() {
        return store.size();
    }

    public synchronized long countCurrent() {
        return store.values().stream().filter(DailyWaterBalance::current).count();
    }
}
