package com.irrigation.application.service;

import com.irrigation.application.dto.CropInput;
import com.irrigation.application.dto.CropOutput;
import com.irrigation.application.dto.CropStageInput;
import com.irrigation.application.pagination.PageParams;
import com.irrigation.application.pagination.PagedResult;
import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.repository.CropRepository;
import com.irrigation.domain.repository.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de gerenciamento de culturas e seus estágios (Req 2).
 */
@Service
public class CropService {

    private static final Logger log = LoggerFactory.getLogger(CropService.class);
    private static final String ENTITY = "Cultura";
    private static final int MAX_STAGES = 20;

    private final CropRepository crops;

    public CropService(CropRepository crops) {
        this.crops = crops;
    }

    @Transactional
    public CropOutput create(CropInput input) {
        Crop crop = crops.save(buildCrop(UUID.randomUUID(), input));
        log.info("Cultura criada: id={}", crop.getId());
        return CropOutput.from(crop);
    }

    @Transactional
    public CropOutput update(UUID id, CropInput input) {
        Crop existing = findOrThrow(id);
        Crop crop = crops.save(buildCrop(existing.getId(), input));
        log.info("Cultura atualizada: id={}", crop.getId());
        return CropOutput.from(crop);
    }

    @Transactional
    public void delete(UUID id) {
        findOrThrow(id);
        crops.deleteById(id);
        log.info("Cultura excluída: id={}", id);
    }

    @Transactional(readOnly = true)
    public CropOutput getById(UUID id) {
        return CropOutput.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PagedResult<CropOutput> list(Integer page, Integer size) {
        PageRequest pageRequest = PageParams.resolve(page, size);
        var content = crops.findAll(pageRequest).stream().map(CropOutput::from).toList();
        return PagedResult.of(content, pageRequest.page(), pageRequest.size(), crops.count());
    }

    private Crop findOrThrow(UUID id) {
        return crops.findById(id).orElseThrow(() -> new EntityNotFoundException(ENTITY, id));
    }

    private Crop buildCrop(UUID id, CropInput in) {
        ViolationCollector v = new ViolationCollector();
        v.check("name", () -> Crop.validateName(in.name()));
        v.check("variety", () -> Crop.validateVariety(in.variety()));
        // Req 2.10: KC e Z são obrigatórios na API; faixa e escala (até 2 casas, A-08) vêm do domínio.
        Kc kc = v.build("kc", in.kc(), Kc::new);
        if (kc != null) {
            Kc validKc = kc;
            if (!v.check("kc", () -> Crop.validateKc(validKc))) {
                kc = null;
            }
        }
        RootDepth rootDepth = v.build("rootDepthMm", in.rootDepth(), RootDepth::new);
        if (rootDepth != null) {
            RootDepth validDepth = rootDepth;
            if (!v.check("rootDepthMm", () -> Crop.validateRootDepth(validDepth))) {
                rootDepth = null;
            }
        }
        List<CropStage> stages = buildStages(v, in.stages());

        // Todas as regras já foram verificadas campo a campo; sem violações, o agregado é construído.
        v.throwIfAny();
        return new Crop(id, in.name(), in.variety(), in.cultivationCycleDays(), stages, kc, rootDepth);
    }

    private List<CropStage> buildStages(ViolationCollector v, List<CropStageInput> inputs) {
        List<CropStage> stages = new ArrayList<>();
        if (inputs == null || inputs.isEmpty()) {
            // Req 2.4, 2.5: zero estágios é aceito
            return stages;
        }
        boolean allBuilt = true;
        for (int i = 0; i < inputs.size(); i++) {
            String prefix = "stages[" + i + "]";
            CropStageInput s = inputs.get(i);
            if (s == null) {
                v.add(prefix, "é obrigatório");
                allBuilt = false;
                continue;
            }
            // Cada campo do estágio é validado de forma independente, para reportar todos os erros.
            boolean nameOk = v.check(prefix + ".name", () -> CropStage.validateName(s.name()));
            Kc kc = v.build(prefix + ".kc", s.kc(), Kc::new);
            Integer order = v.require(prefix + ".stageOrder", s.stageOrder());
            boolean orderOk = order != null
                    && v.check(prefix + ".stageOrder", () -> CropStage.validateStageOrder(order));
            if (nameOk && kc != null && orderOk) {
                stages.add(new CropStage(s.name(), kc, order));
            } else {
                allBuilt = false;
            }
        }
        // Regras da coleção (no máximo 20 estágios, ordem única) só fazem sentido com todos os estágios válidos.
        if (allBuilt) {
            v.check("stages", () -> Crop.validateStages(stages));
        } else if (inputs.size() > MAX_STAGES) {
            // Req 2.11: o limite vale mesmo quando outros estágios têm erros
            v.add("stages", "a cultura deve conter no máximo " + MAX_STAGES + " estágios");
        }
        return stages;
    }
}
