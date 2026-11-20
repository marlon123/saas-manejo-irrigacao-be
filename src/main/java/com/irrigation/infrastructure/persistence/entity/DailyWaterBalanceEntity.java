package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code daily_water_balance}. Separado do agregado de domínio
 * {@link com.irrigation.domain.model.DailyWaterBalance}.
 *
 * <p>O registro é imutável (triggers da migração V6). Por isso todas as colunas são
 * {@code updatable = false}, exceto {@code is_current}, que só muda por consulta em lote
 * ({@code DailyWaterBalanceJpaRepository#markNotCurrent}).
 *
 * <p>ETc, CAD, AFDi, AFDf e I são {@code NUMERIC} sem escala no banco. O mapeamento omite
 * {@code precision} e {@code scale} de propósito: o Hibernate só os usa para gerar DDL (aqui o
 * esquema é do Flyway e a validação compara apenas o tipo), e o valor é lido e gravado como
 * {@link BigDecimal} sem perda de casas.
 *
 * <p>As datas e horas ficam em UTC. {@code calculated_at} é {@code TIMESTAMP} sem fuso.
 */
@Entity
@Table(name = "daily_water_balance")
public class DailyWaterBalanceEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false, updatable = false)
    private UUID areaId;

    @Column(name = "balance_date", nullable = false, updatable = false)
    private LocalDate balanceDate;

    @Column(name = "crop_id", nullable = false, updatable = false)
    private UUID cropId;

    @Column(name = "soil_profile_id", nullable = false, updatable = false)
    private UUID soilProfileId;

    @Column(name = "management_plan_id", nullable = false, updatable = false)
    private UUID managementPlanId;

    @Column(name = "cycle_day_tr", nullable = false, updatable = false)
    private int cycleDayTr;

    // Entradas (snapshot, mesma escala da coluna de origem)
    @Column(name = "etp_mm", nullable = false, updatable = false, precision = 8, scale = 3)
    private BigDecimal etpMm;

    @Column(name = "precipitation_mm", nullable = false, updatable = false, precision = 8, scale = 3)
    private BigDecimal precipitationMm;

    @Column(name = "applied_irrigation_mm", nullable = false, updatable = false, precision = 8, scale = 3)
    private BigDecimal appliedIrrigationMm;

    @Column(name = "field_capacity_pct", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal fieldCapacityPct;

    @Column(name = "wilting_point_pct", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal wiltingPointPct;

    @Column(name = "bulk_density_g_cm3", nullable = false, updatable = false, precision = 6, scale = 3)
    private BigDecimal bulkDensityGCm3;

    @Column(name = "root_depth_mm", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal rootDepthMm;

    @Column(name = "kc", nullable = false, updatable = false, precision = 4, scale = 2)
    private BigDecimal kc;

    @Column(name = "f_factor", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal fFactor;

    @Column(name = "sprinkler_rate_mm_h", nullable = false, updatable = false, precision = 10, scale = 4)
    private BigDecimal sprinklerRateMmH;

    // Resultados: NUMERIC sem escala (sem precision/scale)
    @Column(name = "etc_mm", nullable = false, updatable = false)
    private BigDecimal etcMm;

    @Column(name = "cad_mm", nullable = false, updatable = false)
    private BigDecimal cadMm;

    @Column(name = "afd_initial_mm", nullable = false, updatable = false)
    private BigDecimal afdInitialMm;

    @Column(name = "afd_final_mm", nullable = false, updatable = false)
    private BigDecimal afdFinalMm;

    @Column(name = "ideal_irrigation_mm", nullable = false, updatable = false)
    private BigDecimal idealIrrigationMm;

    @Column(name = "irrigation_recommended", nullable = false, updatable = false)
    private boolean irrigationRecommended;

    @Column(name = "no_recommendation_reason", length = 50, updatable = false)
    private String noRecommendationReason;

    @Column(name = "operating_time_hours", nullable = false, updatable = false, precision = 24, scale = 10)
    private BigDecimal operatingTimeHours;

    // Controle
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "methodology_id", nullable = false, updatable = false, length = 100)
    private String methodologyId;

    @Column(name = "methodology_version", nullable = false, updatable = false, length = 50)
    private String methodologyVersion;

    @Column(name = "revision", nullable = false, updatable = false)
    private int revision;

    /** Única coluna alterável, e só por consulta em lote (true para false). */
    @Column(name = "is_current", nullable = false)
    private boolean current;

    protected DailyWaterBalanceEntity() {
        // exigido pelo JPA
    }

    public static DailyWaterBalanceEntity newInstance() {
        return new DailyWaterBalanceEntity();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public LocalDate getBalanceDate() { return balanceDate; }
    public void setBalanceDate(LocalDate balanceDate) { this.balanceDate = balanceDate; }
    public UUID getCropId() { return cropId; }
    public void setCropId(UUID cropId) { this.cropId = cropId; }
    public UUID getSoilProfileId() { return soilProfileId; }
    public void setSoilProfileId(UUID soilProfileId) { this.soilProfileId = soilProfileId; }
    public UUID getManagementPlanId() { return managementPlanId; }
    public void setManagementPlanId(UUID managementPlanId) { this.managementPlanId = managementPlanId; }
    public int getCycleDayTr() { return cycleDayTr; }
    public void setCycleDayTr(int cycleDayTr) { this.cycleDayTr = cycleDayTr; }
    public BigDecimal getEtpMm() { return etpMm; }
    public void setEtpMm(BigDecimal etpMm) { this.etpMm = etpMm; }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public void setPrecipitationMm(BigDecimal precipitationMm) { this.precipitationMm = precipitationMm; }
    public BigDecimal getAppliedIrrigationMm() { return appliedIrrigationMm; }
    public void setAppliedIrrigationMm(BigDecimal appliedIrrigationMm) { this.appliedIrrigationMm = appliedIrrigationMm; }
    public BigDecimal getFieldCapacityPct() { return fieldCapacityPct; }
    public void setFieldCapacityPct(BigDecimal fieldCapacityPct) { this.fieldCapacityPct = fieldCapacityPct; }
    public BigDecimal getWiltingPointPct() { return wiltingPointPct; }
    public void setWiltingPointPct(BigDecimal wiltingPointPct) { this.wiltingPointPct = wiltingPointPct; }
    public BigDecimal getBulkDensityGCm3() { return bulkDensityGCm3; }
    public void setBulkDensityGCm3(BigDecimal bulkDensityGCm3) { this.bulkDensityGCm3 = bulkDensityGCm3; }
    public BigDecimal getRootDepthMm() { return rootDepthMm; }
    public void setRootDepthMm(BigDecimal rootDepthMm) { this.rootDepthMm = rootDepthMm; }
    public BigDecimal getKc() { return kc; }
    public void setKc(BigDecimal kc) { this.kc = kc; }
    public BigDecimal getFFactor() { return fFactor; }
    public void setFFactor(BigDecimal fFactor) { this.fFactor = fFactor; }
    public BigDecimal getSprinklerRateMmH() { return sprinklerRateMmH; }
    public void setSprinklerRateMmH(BigDecimal sprinklerRateMmH) { this.sprinklerRateMmH = sprinklerRateMmH; }
    public BigDecimal getEtcMm() { return etcMm; }
    public void setEtcMm(BigDecimal etcMm) { this.etcMm = etcMm; }
    public BigDecimal getCadMm() { return cadMm; }
    public void setCadMm(BigDecimal cadMm) { this.cadMm = cadMm; }
    public BigDecimal getAfdInitialMm() { return afdInitialMm; }
    public void setAfdInitialMm(BigDecimal afdInitialMm) { this.afdInitialMm = afdInitialMm; }
    public BigDecimal getAfdFinalMm() { return afdFinalMm; }
    public void setAfdFinalMm(BigDecimal afdFinalMm) { this.afdFinalMm = afdFinalMm; }
    public BigDecimal getIdealIrrigationMm() { return idealIrrigationMm; }
    public void setIdealIrrigationMm(BigDecimal idealIrrigationMm) { this.idealIrrigationMm = idealIrrigationMm; }
    public boolean isIrrigationRecommended() { return irrigationRecommended; }
    public void setIrrigationRecommended(boolean irrigationRecommended) { this.irrigationRecommended = irrigationRecommended; }
    public String getNoRecommendationReason() { return noRecommendationReason; }
    public void setNoRecommendationReason(String noRecommendationReason) { this.noRecommendationReason = noRecommendationReason; }
    public BigDecimal getOperatingTimeHours() { return operatingTimeHours; }
    public void setOperatingTimeHours(BigDecimal operatingTimeHours) { this.operatingTimeHours = operatingTimeHours; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public String getMethodologyId() { return methodologyId; }
    public void setMethodologyId(String methodologyId) { this.methodologyId = methodologyId; }
    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
