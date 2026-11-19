package com.irrigation.application.service;

import com.irrigation.application.dto.DailyWeatherInput;
import com.irrigation.application.dto.DailyWeatherOutput;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.DailyWeather;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.DailyWeatherRepository;
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
 * Casos de uso da meteorologia diária manual (ETP e precipitação) de uma área (Req 5 do módulo de
 * balanço hídrico).
 *
 * <p>Ordem das verificações: validação (400), área (404). ETP e P são obrigatórios; nulo nunca vira zero.
 * A regravação de uma data substitui os valores (upsert) e não altera balanços já calculados (Req 5.8).
 */
@Service
public class DailyWeatherService {

    private static final Logger log = LoggerFactory.getLogger(DailyWeatherService.class);
    private static final String ENTITY = "Meteorologia diária";

    /** Tamanho máximo do período de consulta, em dias, inclusive nas pontas (Req 5.10). */
    static final long MAX_PERIOD_DAYS = 366;

    private final DailyWeatherRepository weatherRepository;
    private final AreaRepository areas;
    private final Clock clock;

    public DailyWeatherService(DailyWeatherRepository weatherRepository, AreaRepository areas, Clock clock) {
        this.weatherRepository = weatherRepository;
        this.areas = areas;
        this.clock = clock;
    }

    /**
     * Insere ou substitui a meteorologia da área na data e devolve o registro persistido (Req 5.1, 5.7).
     * O controlador decide o status HTTP.
     */
    @Transactional
    public DailyWeatherOutput upsert(UUID areaId, DailyWeatherInput input) {
        ViolationCollector v = new ViolationCollector();
        if (input == null) {
            v.add("date", "é obrigatório");
            v.add("etpMm", "é obrigatório");
            v.add("precipitationMm", "é obrigatório");
            v.throwIfAny();
        }
        LocalDate date = v.require("date", input.date());
        if (date != null) {
            v.check("date", () -> requireNotAfterToday(date, today()));
        }
        EtpDepth etp = v.build("etpMm", input.etpMm(), EtpDepth::new);
        PrecipitationDepth precipitation = v.build("precipitationMm", input.precipitationMm(), PrecipitationDepth::new);
        v.throwIfAny();

        requireArea(areaId);

        DailyWeather saved = weatherRepository.upsert(new DailyWeather(UUID.randomUUID(), areaId, date, etp, precipitation));
        log.info("Meteorologia diária registrada: areaId={}, date={}", areaId, date);
        return DailyWeatherOutput.from(saved);
    }

    /** Registros da área no período, inclusive, em ordem crescente de data (Req 5.9 a 5.11). */
    @Transactional(readOnly = true)
    public List<DailyWeatherOutput> findByPeriod(UUID areaId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        requireArea(areaId);
        return weatherRepository.findByAreaAndPeriod(areaId, from, to).stream()
                .map(DailyWeatherOutput::from)
                .toList();
    }

    /** Registro da área na data; 404 se a área ou o registro não existir. */
    @Transactional(readOnly = true)
    public DailyWeatherOutput findByDate(UUID areaId, LocalDate date) {
        if (date == null) {
            ViolationCollector v = new ViolationCollector();
            v.add("date", "é obrigatório");
            v.throwIfAny();
        }
        requireArea(areaId);
        return weatherRepository.findByAreaAndDate(areaId, date)
                .map(DailyWeatherOutput::from)
                .orElseThrow(() -> new EntityNotFoundException(ENTITY, "areaId=" + areaId + ", date=" + date));
    }

    private void requireArea(UUID areaId) {
        if (areaId == null || !areas.existsById(areaId)) {
            throw new EntityNotFoundException("Área", areaId);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private static void requireNotAfterToday(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("não pode ser posterior à data atual (UTC)");
        }
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
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
    }
}
