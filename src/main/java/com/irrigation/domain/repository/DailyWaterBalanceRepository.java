package com.irrigation.domain.repository;

import com.irrigation.domain.model.DailyWaterBalance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de repositório para {@link DailyWaterBalance}.
 * Os registros são imutáveis: não há operação de atualização de valores nem de exclusão.
 * A única alteração permitida é desmarcar o balanço corrente ({@link #markNotCurrent}).
 */
public interface DailyWaterBalanceRepository {

    /** Insere um balanço e devolve o registro persistido. */
    DailyWaterBalance insert(DailyWaterBalance balance);

    /** Insere todos os balanços na mesma transação e devolve os registros persistidos. */
    List<DailyWaterBalance> insertAll(List<DailyWaterBalance> balances);

    /** Balanço corrente da área na data. */
    Optional<DailyWaterBalance> findCurrent(UUID areaId, LocalDate date);

    /** Balanços correntes da área a partir da data (inclusive), em ordem crescente de data. */
    List<DailyWaterBalance> findCurrentFrom(UUID areaId, LocalDate date);

    /** Balanços correntes no período (inclusivo), em ordem crescente de data, paginados. */
    List<DailyWaterBalance> findCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to, PageRequest pageRequest);

    /** Total de balanços correntes no período (inclusivo). */
    long countCurrentByPeriod(UUID areaId, LocalDate from, LocalDate to);

    /** Todas as revisões da área na data, da mais recente para a mais antiga. */
    List<DailyWaterBalance> findRevisions(UUID areaId, LocalDate date);

    /**
     * Marca como não correntes, em lote, os balanços correntes da área a partir da data (inclusive).
     * Devolve a quantidade de registros alterados.
     */
    int markNotCurrent(UUID areaId, LocalDate fromDate);

    /**
     * Primeira data do período (inclusivo) sem balanço corrente, se houver.
     */
    Optional<LocalDate> firstMissingDate(UUID areaId, LocalDate fromDate, LocalDate toDate);
}
