package com.irrigation.application.service;

import com.irrigation.application.dto.AreaInput;
import com.irrigation.application.dto.AreaOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Area;
import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de gerenciamento de áreas de cultivo (Req 1).
 */
@Service
public class AreaService {

    private static final Logger log = LoggerFactory.getLogger(AreaService.class);
    private static final String ENTITY = "Área";

    /** Campos de ordenação válidos (Req 1.5). */
    static final Set<String> SORTABLE_FIELDS = Set.of("name", "areaHectares");

    private final AreaRepository areas;

    public AreaService(AreaRepository areas) {
        this.areas = areas;
    }

    @Transactional
    public AreaOutput create(AreaInput input) {
        Area area = areas.save(buildArea(UUID.randomUUID(), input));
        log.info("Área criada: id={}", area.getId());
        return AreaOutput.from(area);
    }

    @Transactional
    public AreaOutput update(UUID id, AreaInput input) {
        Area existing = findOrThrow(id);
        Area area = areas.save(buildArea(existing.getId(), input));
        log.info("Área atualizada: id={}", area.getId());
        return AreaOutput.from(area);
    }

    @Transactional
    public void delete(UUID id) {
        findOrThrow(id);
        if (areas.isReferencedByRecommendations(id)) {
            throw new BusinessRuleException(
                    "A área " + id + " não pode ser excluída porque é referenciada por recomendações de irrigação");
        }
        areas.deleteById(id);
        log.info("Área excluída: id={}", id);
    }

    @Transactional(readOnly = true)
    public AreaOutput getById(UUID id) {
        return AreaOutput.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<AreaOutput> list(Integer page, Integer size, String sortBy, String sortDirection) {
        PageRequest pageRequest = PageParams.resolve(page, size);
        validateSort(sortBy, sortDirection);
        var content = areas.findAll(pageRequest, sortBy, sortDirection).stream().map(AreaOutput::from).toList();
        return PagedResult.of(content, pageRequest.page(), pageRequest.size(), areas.count());
    }

    private Area findOrThrow(UUID id) {
        return areas.findById(id).orElseThrow(() -> new EntityNotFoundException(ENTITY, id));
    }

    private void validateSort(String sortBy, String sortDirection) {
        ViolationCollector v = new ViolationCollector();
        if (sortBy != null && !SORTABLE_FIELDS.contains(sortBy)) {
            v.add("sort", "campo de ordenação inválido: " + sortBy + ". Valores aceitos: " + SORTABLE_FIELDS);
        }
        if (sortDirection != null
                && !"asc".equalsIgnoreCase(sortDirection) && !"desc".equalsIgnoreCase(sortDirection)) {
            v.add("direction", "direção de ordenação inválida: use asc ou desc");
        }
        v.throwIfAny();
    }

    private Area buildArea(UUID id, AreaInput in) {
        ViolationCollector v = new ViolationCollector();
        v.check("name", () -> Area.validateName(in.name()));
        AreaInHectares hectares = v.build("areaHectares", in.areaHectares(), AreaInHectares::new);
        FlowRate flowRate = v.build("systemFlowRate", in.systemFlowRate(), FlowRate::new);
        IrrigationSystemType type = v.require("irrigationSystemType", in.irrigationSystemType());
        IrrigationEfficiency efficiency =
                v.buildOptional("irrigationEfficiency", in.irrigationEfficiency(), IrrigationEfficiency::new);
        GeographicLocation location = buildLocation(v, in.latitude(), in.longitude());

        // Todas as regras já foram verificadas campo a campo; sem violações, o agregado é construído.
        v.throwIfAny();
        return new Area(id, in.name(), hectares, location, in.generalCharacteristics(), type, flowRate, efficiency);
    }

    private GeographicLocation buildLocation(ViolationCollector v, BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            v.add("geographicLocation", "latitude e longitude devem ser informadas em conjunto");
            return null;
        }
        return v.attempt("geographicLocation", () -> new GeographicLocation(latitude, longitude));
    }
}
