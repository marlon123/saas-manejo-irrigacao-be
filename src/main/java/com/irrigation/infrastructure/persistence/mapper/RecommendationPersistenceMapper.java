package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.model.valueobjects.CalculationInput;
import com.irrigation.domain.model.valueobjects.CalculationResult;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.infrastructure.persistence.entity.CalculationInputJson;
import com.irrigation.infrastructure.persistence.entity.CalculationResultJson;
import com.irrigation.infrastructure.persistence.entity.RecommendationEntity;
import org.springframework.stereotype.Component;

/**
 * Converte entre {@link RecommendationEntity} (persistência, snapshots JSONB) e
 * {@link Recommendation} (domínio).
 */
@Component
public class RecommendationPersistenceMapper {

    public Recommendation toDomain(RecommendationEntity entity) {
        CalculationInputJson in = entity.getInputData();
        CalculationInput input = new CalculationInput(
                in.eto(),
                in.kc() != null ? new Kc(in.kc()) : null,
                in.etc(),
                in.effectivePrecipitation(),
                in.currentSoilMoisture(),
                in.waterStorageCapacity(),
                in.irrigationEfficiency() != null ? new IrrigationEfficiency(in.irrigationEfficiency()) : null,
                in.netIrrigationDepth(),
                in.grossIrrigationDepth(),
                in.irrigatedArea(),
                in.systemFlowRate(),
                in.irrigationTime());

        CalculationResultJson out = entity.getResults();
        CalculationResult result = new CalculationResult(
                out.irrigationDepth(), out.waterVolume(), out.irrigationTime());

        return new Recommendation(
                entity.getId(),
                entity.getAreaId(),
                entity.getDateTime(),
                input,
                result,
                new MethodologyIdentifier(entity.getMethodologyId()),
                entity.getMethodologyVersion());
    }

    public RecommendationEntity toEntity(Recommendation recommendation) {
        CalculationInput in = recommendation.getInputData();
        CalculationInputJson inputJson = new CalculationInputJson(
                in.getEto(),
                in.getKc() != null ? in.getKc().value() : null,
                in.getEtc(),
                in.getEffectivePrecipitation(),
                in.getCurrentSoilMoisture(),
                in.getWaterStorageCapacity(),
                in.getIrrigationEfficiency() != null ? in.getIrrigationEfficiency().value() : null,
                in.getNetIrrigationDepth(),
                in.getGrossIrrigationDepth(),
                in.getIrrigatedArea(),
                in.getSystemFlowRate(),
                in.getIrrigationTime());

        CalculationResult out = recommendation.getResults();
        CalculationResultJson resultJson = new CalculationResultJson(
                out.getIrrigationDepth(), out.getWaterVolume(), out.getIrrigationTime());

        RecommendationEntity entity = RecommendationEntity.newInstance();
        entity.setId(recommendation.getId());
        entity.setAreaId(recommendation.getAreaId());
        entity.setDateTime(recommendation.getDateTime());
        entity.setInputData(inputJson);
        entity.setResults(resultJson);
        entity.setMethodologyId(recommendation.getMethodologyId().value());
        entity.setMethodologyVersion(recommendation.getMethodologyVersion());
        return entity;
    }
}
