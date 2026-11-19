package com.irrigation.application.service;

import com.irrigation.application.dto.SoilProfileInput;
import com.irrigation.application.dto.SoilProfileOutput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.SoilProfile;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;
import com.irrigation.domain.repository.PageRequest;
import com.irrigation.domain.repository.SoilProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        // Par volumétrico (Req 1.7, 1.8): ambos ou nenhum.
        VolumetricWaterContent fieldCapacity =
                v.buildOptional("fieldCapacity", in.fieldCapacity(), VolumetricWaterContent::new);
        VolumetricWaterContent wiltingPoint =
                v.buildOptional("permanentWiltingPoint", in.permanentWiltingPoint(), VolumetricWaterContent::new);
        // Par gravimétrico (Req 1.2, 1.9, A-07, A-08): ambos ou nenhum, CC > PMP e até 2 casas decimais.
        GravimetricWaterContent fieldCapacityGravimetric = v.buildOptional(
                "fieldCapacityGravimetricPct", in.fieldCapacityGravimetric(), GravimetricWaterContent::new);
        GravimetricWaterContent wiltingPointGravimetric = v.buildOptional(
                "permanentWiltingPointGravimetricPct", in.permanentWiltingPointGravimetric(),
                GravimetricWaterContent::new);
        BulkDensity bulkDensity = v.build("bulkDensity", in.bulkDensity(), BulkDensity::new);
        RootDepth rootDepth = v.buildOptional("effectiveRootDepth", in.effectiveRootDepth(), RootDepth::new);
        AvailableWater availableWater =
                v.buildOptional("availableWater", in.availableWater(), AvailableWater::new);

        checkPair(v, "fieldCapacity", in.fieldCapacity(), "permanentWiltingPoint", in.permanentWiltingPoint(),
                () -> SoilProfile.validateWaterContents(fieldCapacity, wiltingPoint),
                fieldCapacity != null && wiltingPoint != null);
        checkPair(v, "fieldCapacityGravimetricPct", in.fieldCapacityGravimetric(),
                "permanentWiltingPointGravimetricPct", in.permanentWiltingPointGravimetric(),
                () -> SoilProfile.validateGravimetricContents(fieldCapacityGravimetric, wiltingPointGravimetric),
                fieldCapacityGravimetric != null && wiltingPointGravimetric != null);

        // Todas as regras já foram verificadas campo a campo; sem violações, o agregado é construído.
        v.throwIfAny();
        return new SoilProfile(id, in.soilType(), fieldCapacity, wiltingPoint, fieldCapacityGravimetric,
                wiltingPointGravimetric, bulkDensity, rootDepth, availableWater, in.soilWaterUnit(),
                in.bulkDensityUnit(), in.rootDepthUnit(), in.availableWaterUnit());
    }

    /**
     * Valida um par "ambos ou nenhum" com CC maior que PMP. O par incompleto é decidido pelos valores brutos
     * (um valor presente e inválido já gerou a própria violação) e é reportado nos dois campos do par.
     * A relação CC &gt; PMP só é avaliada quando os dois valores individuais são válidos.
     */
    private static void checkPair(ViolationCollector v, String fieldCapacityName, BigDecimal rawFieldCapacity,
                                  String wiltingPointName, BigDecimal rawWiltingPoint,
                                  Runnable relationCheck, boolean bothValid) {
        if ((rawFieldCapacity == null) != (rawWiltingPoint == null)) {
            String message = "par incompleto: " + fieldCapacityName + " e " + wiltingPointName
                    + " devem ser informados em conjunto";
            v.add(fieldCapacityName, message);
            v.add(wiltingPointName, message);
        } else if (bothValid) {
            // Req 1.9: CC deve ser maior que PMP
            v.check(fieldCapacityName, relationCheck);
        }
    }
}