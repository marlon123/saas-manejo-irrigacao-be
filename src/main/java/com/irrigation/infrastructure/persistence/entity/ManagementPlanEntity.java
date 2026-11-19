package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code management_plans}. Separado do agregado de domínio
 * {@link com.irrigation.domain.model.ManagementPlan}. As colunas {@code created_at} e
 * {@code updated_at} são mantidas só pelo banco e não são mapeadas.
 */
@Entity
@Table(name = "management_plans")
public class ManagementPlanEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(name = "soil_profile_id", nullable = false)
    private UUID soilProfileId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "f_factor", nullable = false, precision = 5, scale = 4)
    private BigDecimal availabilityFactor;

    @Column(name = "sprinkler_rate_mm_h", nullable = false, precision = 10, scale = 4)
    private BigDecimal sprinklerRate;

    protected ManagementPlanEntity() {
        // exigido pelo JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public UUID getCropId() { return cropId; }
    public void setCropId(UUID cropId) { this.cropId = cropId; }
    public UUID getSoilProfileId() { return soilProfileId; }
    public void setSoilProfileId(UUID soilProfileId) { this.soilProfileId = soilProfileId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public BigDecimal getAvailabilityFactor() { return availabilityFactor; }
    public void setAvailabilityFactor(BigDecimal availabilityFactor) { this.availabilityFactor = availabilityFactor; }
    public BigDecimal getSprinklerRate() { return sprinklerRate; }
    public void setSprinklerRate(BigDecimal sprinklerRate) { this.sprinklerRate = sprinklerRate; }

    public static ManagementPlanEntity newInstance() {
        return new ManagementPlanEntity();
    }
}
