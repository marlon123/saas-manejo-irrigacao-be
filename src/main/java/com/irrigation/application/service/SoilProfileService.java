package com.irrigation.application.service;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso de gerenciamento de perfis de solo (Req 3).
 */
@Service
public class SoilProfileService {

    private static final Logger log = LoggerFactory.getLogger(SoilProfileService.class);
    private static final String ENTITY = "Perfil de solo";

    private final SoilProfileRepository soilProfiles;

    public SoilProfileService(SoilProfileRepository soilProfiles) {
        this.soilProfiles = soilProfiles;
    }

    @Transactional
    public SoilProfileOutput create(SoilProfileInput input) {
        SoilProfile soil = soilProfiles.save(buildSoilProfile(UUID.randomUUID(), input));
        log.info("Perfil de solo criado: id={}", soil.getId());
        return SoilProfileOutput.from(soil);
    }

    @Transactional
    public SoilProfileOutput update(UUID id, SoilProfileInput input) {
        SoilProfile existing = findOrThrow(id);
        SoilProfile soil = soilProfiles.save(buildSoilProfile(existing.getId(), input));
        log.info("Perfil de solo atualizado: id={}", soil.getId());
        return SoilProfileOutput.from(soil);
    }

    @Transactional
    public void delete(UUID id) {
        findOrThrow(id);
        soilProfiles.deleteById(id);
        log.info("Perfil de solo excluído: id={}", id);
    }

    @Transactional(readOnly = true)
    public SoilProfileOutput getById(UUID id) {
        return SoilProfileOutput.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<SoilProfileOutput> list(Integer page, Integer size) {
        PageRequest pageRequest = PageParams.resolve(page, size);
        var content = soilProfiles.findAll(pageRequest).stream().map(SoilProfileOutput::from).toList();
        return PagedResult.of(content, pageRequest.page(), pageRequest.size(), soilProfiles.count());
    }

    private SoilProfile findOrThrow(UUID id) {
        return soilProfiles.findById(id).orElseThrow(() -> new EntityNotFoundException(ENTITY, id));
    }

    private SoilProfile buildSoilProfile(UUID id, SoilProfileInput in) {
        ViolationCollector v = new ViolationCollector();
        v.check("soilType", () -> SoilProfile.validateSoilType(in.soilType()));
        VolumetricWaterContent fieldCapacity = v.build("fieldCapacity", in.fieldCapacity(), VolumetricWaterContent::new);
        VolumetricWaterContent wiltingPoint =
                v.build("permanentWiltingPoint", in.permanentWiltingPoint(), VolumetricWaterContent::new);
        BulkDensity bulkDensity = v.build("bulkDensity", in.bulkDensity(), BulkDensity::new);
        RootDepth rootDepth = v.build("effectiveRootDepth", in.effectiveRootDepth(), RootDepth::new);
        AvailableWater availableWater = v.build("availableWater", in.availableWater(), AvailableWater::new);
        MeasurementUnit soilWaterUnit = v.require("soilWaterUnit", in.soilWaterUnit());
        MeasurementUnit bulkDensityUnit = v.require("bulkDensityUnit", in.bulkDensityUnit());
        MeasurementUnit rootDepthUnit = v.require("rootDepthUnit", in.rootDepthUnit());
        MeasurementUnit availableWaterUnit = v.require("availableWaterUnit", in.availableWaterUnit());

        if (fieldCapacity != null && wiltingPoint != null) {
            // Req 3.11 — só avaliável quando ambos os valores individuais são válidos
            v.check("fieldCapacity", () -> SoilProfile.validateWaterContents(fieldCapacity, wiltingPoint));
        }

        // Todas as regras já foram verificadas campo a campo; sem violações, o agregado é construído.
        v.throwIfAny();
        return new SoilProfile(id, in.soilType(), fieldCapacity, wiltingPoint, bulkDensity, rootDepth, availableWater,
                soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }
}
