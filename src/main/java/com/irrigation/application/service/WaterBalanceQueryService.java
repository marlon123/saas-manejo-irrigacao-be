package com.irrigation.application.service;

import com.irrigation.application.dto.WaterBalanceOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.DailyWaterBalanceRepository;
import com.irrigation.domain.repository.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consultas da evolução diária do balanço hídrico (Req 13.6 e 14).
 *
 * <p>Ordem das verificações: validação (400, acumulada), área (404), existência do registro (404).
 * Os valores em mm saem com a política de apresentação do Req 15, aplicada por {@link WaterBalanceOutput}.
 * Nenhum valor de página ou tamanho é ajustado automaticamente (Req 14.8).
 */
@Service
public class WaterBalanceQueryService {

    private static final String ENTITY = "Balanço hídrico";

    private final DailyWaterBalanceRepository balances;
    private final AreaRepository areas;

    public WaterBalanceQueryService(DailyWaterBalanceRepository balances, AreaRepository areas) {
        this.balances = balances;
        this.areas = areas;
    }

    /**
     * Balanços correntes da área no período, em ordem crescente de data, paginados (Req 14.1, 14.4, 14.5, 14.7, 14.8).
     *
     * <p>{@code from} e {@code to} são opcionais; {@code null} deixa a respectiva ponta aberta e é repassado ao
     * repositório assim. Todas as violações de validação (período e paginação) são acumuladas numa só resposta 400.
     */
    @Transactional(readOnly = true)
    public PagedResult<WaterBalanceOutput> findByPeriod(
            UUID areaId, LocalDate from, LocalDate to, Integer page, Integer size) {
        ViolationCollector v = new ViolationCollector();
        if (from != null && to != null && from.isAfter(to)) {
            v.add("from", "não pode ser posterior a to");
        }
        if (page != null && page < 0) {
            v.add("page", "deve ser maior ou igual a zero");
        }
        if (size != null && (size < 1 || size > PageRequest.MAX_SIZE)) {
            v.add("size", "deve estar entre 1 e " + PageRequest.MAX_SIZE);
        }
        v.throwIfAny();
        PageRequest pageRequest = PageParams.resolve(page, size);

        requireArea(areaId);
        List<WaterBalanceOutput> content = balances.findCurrentByPeriod(areaId, from, to, pageRequest).stream()
                .map(WaterBalanceOutput::from)
                .toList();
        long total = balances.countCurrentByPeriod(areaId, from, to);
        return PagedResult.of(content, pageRequest.page(), pageRequest.size(), total);
    }

    /** Balanço corrente da área na data; 404 se a área não existir ou a data não tiver cálculo (Req 14.2, 14.3). */
    @Transactional(readOnly = true)
    public WaterBalanceOutput findByDate(UUID areaId, LocalDate date) {
        requireDate(date);
        requireArea(areaId);
        return balances.findCurrent(areaId, date)
                .map(WaterBalanceOutput::from)
                .orElseThrow(() -> notCalculated(areaId, date));
    }

    /**
     * Todas as revisões da data, da mais recente para a mais antiga (Req 13.6).
     * Data sem nenhuma revisão (nunca calculada) gera 404, como a consulta por data.
     */
    @Transactional(readOnly = true)
    public List<WaterBalanceOutput> findRevisions(UUID areaId, LocalDate date) {
        requireDate(date);
        requireArea(areaId);
        List<WaterBalanceOutput> revisions = balances.findRevisions(areaId, date).stream()
                .map(WaterBalanceOutput::from)
                .toList();
        if (revisions.isEmpty()) {
            throw notCalculated(areaId, date);
        }
        return revisions;
    }

    private static void requireDate(LocalDate date) {
        if (date == null) {
            ViolationCollector v = new ViolationCollector();
            v.add("date", "é obrigatório");
            v.throwIfAny();
        }
    }

    private void requireArea(UUID areaId) {
        if (areaId == null || !areas.existsById(areaId)) {
            throw new EntityNotFoundException("Área", areaId);
        }
    }

    private static EntityNotFoundException notCalculated(UUID areaId, LocalDate date) {
        return new EntityNotFoundException(ENTITY, "areaId=" + areaId + ", date=" + date);
    }
}
