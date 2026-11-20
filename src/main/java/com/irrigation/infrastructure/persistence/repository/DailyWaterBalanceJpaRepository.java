package com.irrigation.infrastructure.persistence.repository;

import com.irrigation.infrastructure.persistence.entity.DailyWaterBalanceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyWaterBalanceJpaRepository extends JpaRepository<DailyWaterBalanceEntity, UUID> {

    /** Balanço corrente da área na data. */
    Optional<DailyWaterBalanceEntity> findByAreaIdAndBalanceDateAndCurrentTrue(UUID areaId, LocalDate balanceDate);

    /** Balanços correntes da área a partir da data (inclusiva), em ordem crescente de data. */
    List<DailyWaterBalanceEntity> findByAreaIdAndBalanceDateGreaterThanEqualAndCurrentTrueOrderByBalanceDateAsc(
            UUID areaId, LocalDate from);

    /** Balanços correntes da área no período (inclusivo). A ordenação vem do {@link Pageable}. */
    List<DailyWaterBalanceEntity> findByAreaIdAndBalanceDateBetweenAndCurrentTrue(
            UUID areaId, LocalDate from, LocalDate to, Pageable pageable);

    long countByAreaIdAndBalanceDateBetweenAndCurrentTrue(UUID areaId, LocalDate from, LocalDate to);

    /** Indica se existe algum balanço (corrente ou não) do plano. */
    boolean existsByManagementPlanId(UUID managementPlanId);

    /** Maior data entre os balanços correntes do plano, ou nulo se não houver. */
    @Query("""
            SELECT MAX(b.balanceDate) FROM DailyWaterBalanceEntity b
             WHERE b.managementPlanId = :planId AND b.current = true
            """)
    LocalDate findLastCurrentBalanceDate(@Param("planId") UUID planId);

    /** Todas as revisões da área na data, da mais recente para a mais antiga. */
    List<DailyWaterBalanceEntity> findByAreaIdAndBalanceDateOrderByRevisionDesc(UUID areaId, LocalDate balanceDate);

    /**
     * Marca como não correntes, em lote, os balanços correntes da área a partir da data (inclusiva).
     * É a única alteração aceita pelo trigger de imutabilidade (is_current de true para false).
     * O contexto de persistência é limpo para que leituras seguintes vejam o novo estado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DailyWaterBalanceEntity b
               SET b.current = false
             WHERE b.areaId = :areaId
               AND b.balanceDate >= :fromDate
               AND b.current = true
            """)
    int markNotCurrent(@Param("areaId") UUID areaId, @Param("fromDate") LocalDate fromDate);

    /**
     * Primeira data do intervalo (inclusivo) sem balanço corrente, como texto ISO (aaaa-mm-dd),
     * ou lista vazia se todas as datas têm balanço corrente. Texto evita ambiguidade do tipo
     * que o driver devolve para DATE em consulta nativa; use {@link #firstMissingDate}.
     */
    @Query(value = """
            SELECT to_char(g.d, 'YYYY-MM-DD')
              FROM generate_series(CAST(:fromDate AS timestamp), CAST(:toDate AS timestamp), INTERVAL '1 day') AS g(d)
              LEFT JOIN daily_water_balance b
                     ON b.area_id = :areaId
                    AND b.balance_date = CAST(g.d AS date)
                    AND b.is_current
             WHERE b.id IS NULL
             ORDER BY g.d
             LIMIT 1
            """, nativeQuery = true)
    List<String> findFirstMissingDateText(@Param("areaId") UUID areaId,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    /** Primeira data entre {@code fromDate} e {@code toDate} (inclusivas) sem balanço corrente. */
    default Optional<LocalDate> firstMissingDate(UUID areaId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            return Optional.empty();
        }
        return findFirstMissingDateText(areaId, fromDate, toDate).stream()
                .findFirst()
                .map(LocalDate::parse);
    }
}
