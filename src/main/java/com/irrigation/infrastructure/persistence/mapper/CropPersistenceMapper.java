package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.Crop;
import com.irrigation.domain.model.CropStage;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.infrastructure.persistence.entity.CropEntity;
import com.irrigation.infrastructure.persistence.entity.CropStageEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Converte entre {@link CropEntity}/{@link CropStageEntity} (persistência) e
 * {@link Crop}/{@link CropStage} (domínio).
 *
 * <p>O estágio de domínio não possui identidade própria; ao persistir, cada estágio
 * recebe um novo UUID e a coleção anterior é removida via {@code orphanRemoval}.
 */
@Component
public class CropPersistenceMapper {

    public Crop toDomain(CropEntity entity) {
        List<CropStage> stages = entity.getStages().stream()
                .map(s -> new CropStage(s.getName(), new Kc(s.getKc()), s.getStageOrder()))
                .toList();
        return new Crop(entity.getId(), entity.getName(), entity.getVariety(),
                entity.getCultivationCycleDays(), stages);
    }

    public CropEntity toEntity(Crop crop) {
        CropEntity entity = CropEntity.newInstance();
        entity.setId(crop.getId());
        entity.setName(crop.getName());
        entity.setVariety(crop.getVariety());
        entity.setCultivationCycleDays(crop.getCultivationCycleDays());
        for (CropStage stage : crop.getStages()) {
            CropStageEntity stageEntity = CropStageEntity.newInstance();
            stageEntity.setId(UUID.randomUUID());
            stageEntity.setName(stage.getName());
            stageEntity.setKc(stage.getKc().value());
            stageEntity.setStageOrder(stage.getStageOrder());
            entity.addStage(stageEntity);
        }
        return entity;
    }
}
