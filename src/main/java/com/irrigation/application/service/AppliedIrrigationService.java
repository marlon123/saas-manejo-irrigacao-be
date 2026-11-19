package com.irrigation.application.service;

import com.irrigation.application.dto.AppliedIrrigationInput;
import com.irrigation.application.dto.AppliedIrrigationOutput;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.AppliedIrrigation;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.repository.AppliedIrrigationRepository;
import com.irrigation.domain.repository.AreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso da irrigação real (IR) aplicada numa área (Req 6 do módulo de balanço hídrico).
 *
 * <p>Ordem das verificações: validação (400), área (404). IR é obrigatória; nulo nunca vira zero, e zero
 * informado explicitamente é válido. A regravação de uma data substitui o valor (upsert) e não altera
 * balanços já calculados (Req 6.9). IR é independente da irrigação ideal recomendada (Req 6.8).
 */
@Service
public class AppliedIrrigationService {

    private static final Logger log = LoggerFactory.getLogger(AppliedIrrigationService.class);
    private static final String ENTITY = "Irrigação real";

    /** Tamanho máximo do período de consulta, em dias, inclusive nas pontas. */
    static final long MAX_PERIOD_DAYS = 366;

    private final AppliedIrrigationRepository irrigations;
    private final AreaRepository areas;
    private final Clock clock;

    public AppliedIrrigationService(AppliedIrrigationRepository irrigations, AreaRepository areas, Clock clock) {
        this.irrigations = irrigations;
        this.areas = areas;
        this.clock = clock;
    }

    /**
     * Insere ou substitui a IR da área na data e devolve o registro persistido (Req 6.1, 6.7).
     * O controlador decide o status HTTP.
     */
    @Transactional
    public AppliedIrrigationOutput upsert(UUID areaId, AppliedIrrigationInput input) {
        ViolationCollector v = new ViolationCollector();
        if (input == null) {
            v.add("date", "é obrigatório");
            v.add("appliedIrrigationMm", "é obrigatório");
            v.throwIfAny();
        }
        LocalDate date = v.require("date", input.date());
        if (date != null) {
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            v.check("date", () -> {
                if (date.isAfter(today)) {
                    throw new IllegalArgumentException("não pode ser posterior à data atual (UTC)");
                }
            });
        }
        AppliedIrrigationDepth depth =
                v.build("appliedIrrigationMm", input.appliedIrrigationMm(), AppliedIrrigationDepth::new);
        v.throwIfAny();

        requireArea(areaId);

        AppliedIrrigation saved = irrigations.upsert(new AppliedIrrigation(UUID.randomUUID(), areaId, date, depth));
        log.info("Irrigação real registrada: areaId={}, date={}", areaId, date);
        return AppliedIrrigationOutput.from(saved);
    }

    /** Registros da área no período, inclusive, em ordem crescente de data (Req 6.10 a 6.12). */
    @Transactional(readOnly = true)
    public List<AppliedIrrigationOutput> findByPeriod(UUID areaId, LocalDate from, LocalDate to) {
        ViolationCollector v = new ViolationCollector();
        v.require("from", from);
        v.require("to", to);
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                v.add("from", "não pode ser posterior a to");
            } else if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
                v.add("to", "o período não pode ter mais de " + MAX_PERIOD_DAYS + " dias");
            }
        }
        v.throwIfAny();

        requireArea(areaId);
        return irrigations.findByAreaAndPeriod(areaId, from, to).stream()
                .map(AppliedIrrigationOutput::from)
                .toList();
    }

    /** Registro da área na data; 404 se a área ou o registro não existir. */
    @Transactional(readOnly = true)
    public AppliedIrrigationOutput findByDate(UUID areaId, LocalDate date) {
        if (date == null) {
            ViolationCollector v = new ViolationCollector();
            v.add("date", "é obrigatório");
            v.throwIfAny();
        }
        requireArea(areaId);
        return irrigations.findByAreaAndDate(areaId, date)
                .map(AppliedIrrigationOutput::from)
                .orElseThrow(() -> new EntityNotFoundException(ENTITY, "areaId=" + areaId + ", date=" + date));
    }

    private void requireArea(UUID areaId) {
        if (areaId == null || !areas.existsById(areaId)) {
            throw new EntityNotFoundException("Área", areaId);
        }
    }
}
