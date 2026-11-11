package com.irrigation.application.service;

import com.irrigation.application.dto.CalculateRecommendationInput;
import com.irrigation.application.dto.CalculationParameters;
import com.irrigation.application.dto.RecommendationOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.calculation.CalculationEngine;
import com.irrigation.domain.calculation.CalculationMethod;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de cálculo e histórico de recomendações de irrigação (Req 5).
 *
 * <p>Recomendações são imutáveis: este serviço só cria e consulta, nunca altera (Req 5.7).
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final String ENTITY = "Recomendação";

    /** Limites usados quando o filtro de data é aberto (apenas início ou apenas fim). */
    static final LocalDateTime MIN_DATE_TIME = LocalDateTime.of(1900, 1, 1, 0, 0);
    static final LocalDateTime MAX_DATE_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final CalculationEngine engine;
    private final RecommendationRepository recommendations;
    private final AreaRepository areas;
    private final Clock clock;

    public RecommendationService(
            CalculationEngine engine,
            RecommendationRepository recommendations,
            AreaRepository areas,
            Clock clock) {
        this.engine = engine;
        this.recommendations = recommendations;
        this.areas = areas;
        this.clock = clock;
    }

    /**
     * Executa o cálculo com a metodologia informada e armazena a recomendação (Req 5.1).
     */
    @Transactional
    public RecommendationOutput calculate(CalculateRecommendationInput input) {
        ViolationCollector v = new ViolationCollector();
        UUID areaId = v.require("areaId", input.areaId());
        MethodologyIdentifier methodologyId =
                v.build("methodologyId", input.methodologyId(), MethodologyIdentifier::new);
        CalculationInput calculationInput = buildCalculationInput(v, input.parameters());
        v.throwIfAny();

        if (!areas.existsById(areaId)) {
            throw new EntityNotFoundException("Área", areaId);
        }

        // Valida entradas (Req 4.11) e delega à metodologia registrada (Req 4.5, 4.6)
        CalculationResult result = engine.calculate(methodologyId, calculationInput);
        CalculationMethod method = engine.getRegisteredMethods().get(methodologyId);

        Recommendation recommendation = recommendations.save(new Recommendation(
                UUID.randomUUID(),
                areaId,
                LocalDateTime.now(clock),
                calculationInput,
                result,
                methodologyId,
                method.version()));
        log.info("Recomendação calculada: id={}, areaId={}, metodologia={}, versão={}",
                recommendation.getId(), areaId, methodologyId.value(), method.version());
        return RecommendationOutput.from(recommendation);
    }

    @Transactional(readOnly = true)
    public RecommendationOutput getById(UUID id) {
        return recommendations.findById(id)
                .map(RecommendationOutput::from)
                .orElseThrow(() -> new EntityNotFoundException(ENTITY, id));
    }

    /**
     * Lista recomendações filtrando por área, por período, ou por ambos (Req 5.2–5.4).
     * Sem resultados, retorna página vazia (Req 5.8).
     */
    @Transactional(readOnly = true)
    public PagedResult<RecommendationOutput> list(
            UUID areaId, LocalDateTime dateFrom, LocalDateTime dateTo, Integer page, Integer size) {
        PageRequest pageRequest = PageParams.resolve(page, size);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            ViolationCollector v = new ViolationCollector();
            v.add("dateFrom", "não pode ser posterior a dateTo");
            v.throwIfAny();
        }

        boolean hasDateFilter = dateFrom != null || dateTo != null;
        LocalDateTime from = dateFrom != null ? dateFrom : MIN_DATE_TIME;
        LocalDateTime to = dateTo != null ? dateTo : MAX_DATE_TIME;

        List<Recommendation> content;
        long total;
        if (areaId != null && hasDateFilter) {
            content = recommendations.findByAreaAndDateRange(areaId, from, to, pageRequest);
            total = recommendations.countByAreaAndDateRange(areaId, from, to);
        } else if (areaId != null) {
            content = recommendations.findByArea(areaId, pageRequest);
            total = recommendations.countByArea(areaId);
        } else {
            content = recommendations.findByDateRange(from, to, pageRequest);
            total = recommendations.countByDateRange(from, to);
        }
        return PagedResult.of(
                content.stream().map(RecommendationOutput::from).toList(),
                pageRequest.page(), pageRequest.size(), total);
    }

    private CalculationInput buildCalculationInput(ViolationCollector v, CalculationParameters p) {
        if (p == null) {
            v.add("parameters", "é obrigatório");
            return null;
        }
        // Kc e eficiência são opcionais aqui: se ausentes, o motor de cálculo os reporta como inválidos.
        Kc kc = v.buildOptional("kc", p.kc(), Kc::new);
        IrrigationEfficiency efficiency =
                v.buildOptional("irrigationEfficiency", p.irrigationEfficiency(), IrrigationEfficiency::new);
        return new CalculationInput(
                p.eto(), kc, p.etc(), p.effectivePrecipitation(), p.currentSoilMoisture(),
                p.waterStorageCapacity(), efficiency, p.netIrrigationDepth(), p.grossIrrigationDepth(),
                p.irrigatedArea(), p.systemFlowRate(), p.irrigationTime());
    }
}
