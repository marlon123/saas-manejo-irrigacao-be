package com.irrigation.infrastructure.persistence.mapper;

import com.irrigation.domain.model.DailyWaterBalance;
import com.irrigation.domain.model.valueobjects.AppliedIrrigationDepth;
import com.irrigation.domain.model.valueobjects.AvailabilityFactor;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.CycleDay;
import com.irrigation.domain.model.valueobjects.EtpDepth;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.PrecipitationDepth;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.SprinklerRate;
import com.irrigation.domain.waterbalance.NoRecommendationReason;
import com.irrigation.domain.waterbalance.WaterBalanceInputs;
import com.irrigation.domain.waterbalance.WaterBalanceResult;
import com.irrigation.infrastructure.persistence.entity.DailyWaterBalanceEntity;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/**
 * Converte entre {@link DailyWaterBalanceEntity} (persistência) e {@link DailyWaterBalance} (domínio).
 *
 * <p>A tabela não tem colunas para {@code firstDayOfPlan} nem {@code previousAfdFinal}. Elas são
 * reconstruídas sem perda: o primeiro dia do plano é o único caso com motivo
 * {@link NoRecommendationReason#FIRST_DAY_OF_FIRST_CYCLE}, e nos demais dias a AFDi é, por
 * definição, a AFDf anterior. Na gravação, essas duas entradas não são persistidas.
 */
@Component
public class DailyWaterBalancePersistenceMapper {

    public DailyWaterBalance toDomain(DailyWaterBalanceEntity entity) {
        NoRecommendationReason reason = entity.getNoRecommendationReason() == null
                ? null
                : NoRecommendationReason.valueOf(entity.getNoRecommendationReason());
        boolean firstDay = reason == NoRecommendationReason.FIRST_DAY_OF_FIRST_CYCLE;

        WaterBalanceInputs inputs = new WaterBalanceInputs(
                new EtpDepth(entity.getEtpMm()),
                new PrecipitationDepth(entity.getPrecipitationMm()),
                new AppliedIrrigationDepth(entity.getAppliedIrrigationMm()),
                new GravimetricWaterContent(entity.getFieldCapacityPct()),
                new GravimetricWaterContent(entity.getWiltingPointPct()),
                new BulkDensity(entity.getBulkDensityGCm3()),
                new RootDepth(entity.getRootDepthMm()),
                new Kc(entity.getKc()),
                new AvailabilityFactor(entity.getFFactor()),
                new SprinklerRate(entity.getSprinklerRateMmH()),
                firstDay,
                firstDay ? null : entity.getAfdInitialMm());

        WaterBalanceResult result = new WaterBalanceResult(
                entity.getEtcMm(),
                entity.getCadMm(),
                entity.getAfdInitialMm(),
                entity.getAfdFinalMm(),
                entity.getIdealIrrigationMm(),
                entity.isIrrigationRecommended(),
                reason,
                entity.getOperatingTimeHours());

        return new DailyWaterBalance(
                entity.getId(),
                entity.getAreaId(),
                entity.getBalanceDate(),
                entity.getCropId(),
                entity.getSoilProfileId(),
                entity.getManagementPlanId(),
                new CycleDay(entity.getCycleDayTr()),
                inputs,
                result,
                entity.getCalculatedAt().toInstant(ZoneOffset.UTC),
                entity.getMethodologyId(),
                entity.getMethodologyVersion(),
                entity.getRevision(),
                entity.isCurrent());
    }

    public DailyWaterBalanceEntity toEntity(DailyWaterBalance balance) {
        WaterBalanceInputs in = balance.inputs();
        WaterBalanceResult out = balance.result();

        DailyWaterBalanceEntity entity = DailyWaterBalanceEntity.newInstance();
        entity.setId(balance.id());
        entity.setAreaId(balance.areaId());
        entity.setBalanceDate(balance.date());
        entity.setCropId(balance.cropId());
        entity.setSoilProfileId(balance.soilProfileId());
        entity.setManagementPlanId(balance.managementPlanId());
        entity.setCycleDayTr(balance.cycleDay().value());

        entity.setEtpMm(in.etp().value());
        entity.setPrecipitationMm(in.precipitation().value());
        entity.setAppliedIrrigationMm(in.appliedIrrigation().value());
        entity.setFieldCapacityPct(in.fieldCapacity().value());
        entity.setWiltingPointPct(in.permanentWiltingPoint().value());
        entity.setBulkDensityGCm3(in.bulkDensity().value());
        entity.setRootDepthMm(in.rootDepth().value());
        entity.setKc(in.kc().value());
        entity.setFFactor(in.availabilityFactor().value());
        entity.setSprinklerRateMmH(in.sprinklerRate().value());

        entity.setEtcMm(out.etc());
        entity.setCadMm(out.cad());
        entity.setAfdInitialMm(out.afdInitial());
        entity.setAfdFinalMm(out.afdFinal());
        entity.setIdealIrrigationMm(out.idealIrrigation());
        entity.setIrrigationRecommended(out.recommended());
        entity.setNoRecommendationReason(
                out.noRecommendationReason() == null ? null : out.noRecommendationReason().name());
        entity.setOperatingTimeHours(out.operatingTimeHours());

        entity.setCalculatedAt(java.time.LocalDateTime.ofInstant(balance.calculatedAt(), ZoneOffset.UTC));
        entity.setMethodologyId(balance.methodologyId());
        entity.setMethodologyVersion(balance.methodologyVersion());
        entity.setRevision(balance.revision());
        entity.setCurrent(balance.current());
        return entity;
    }
}
