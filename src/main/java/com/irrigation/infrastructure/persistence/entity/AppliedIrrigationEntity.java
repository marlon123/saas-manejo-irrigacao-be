package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code applied_irrigation}. Separado do agregado de domínio
 * {@link com.irrigation.domain.model.AppliedIrrigation}. As colunas {@code created_at} e
 * {@code updated_at} são mantidas só pelo banco e não são mapeadas.
 */
@Entity
@Table(name = "applied_irrigation")
public class AppliedIrrigationEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "irrigation_date", nullable = false)
    private LocalDate irrigationDate;

    @Column(name = "applied_irrigation_mm", nullable = false, precision = 8, scale = 3)
    private BigDecimal appliedIrrigationMm;

    protected AppliedIrrigationEntity() {
        // exigido pelo JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public LocalDate getIrrigationDate() { return irrigationDate; }
    public void setIrrigationDate(LocalDate irrigationDate) { this.irrigationDate = irrigationDate; }
    public BigDecimal getAppliedIrrigationMm() { return appliedIrrigationMm; }
    public void setAppliedIrrigationMm(BigDecimal appliedIrrigationMm) { this.appliedIrrigationMm = appliedIrrigationMm; }

    public static AppliedIrrigationEntity newInstance() {
        return new AppliedIrrigationEntity();
    }
}
