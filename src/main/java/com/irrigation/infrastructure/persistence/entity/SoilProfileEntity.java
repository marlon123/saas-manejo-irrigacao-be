package com.irrigation.infrastructure.persistence.entity;

import com.irrigation.domain.model.enums.MeasurementUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code soil_profiles}.
 * Cada parâmetro possui sua unidade de medida explícita (Req 3.14).
 */
@Entity
@Table(name = "soil_profiles")
public class SoilProfileEntity {

    @Id
    private UUID id;

    @Column(name = "soil_type", nullable = false, length = 255)
    private String soilType;

    @Column(name = "field_capacity", nullable = false)
    private BigDecimal fieldCapacity;

    @Column(name = "permanent_wilting_point", nullable = false)
    private BigDecimal permanentWiltingPoint;

    @Column(name = "bulk_density", nullable = false)
    private BigDecimal bulkDensity;

    @Column(name = "effective_root_depth", nullable = false)
    private BigDecimal effectiveRootDepth;

    @Column(name = "available_water")
    private BigDecimal availableWater;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_water_unit", nullable = false, length = 50)
    private MeasurementUnit soilWaterUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "bulk_density_unit", nullable = false, length = 50)
    private MeasurementUnit bulkDensityUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_depth_unit", nullable = false, length = 50)
    private MeasurementUnit rootDepthUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "available_water_unit", nullable = false, length = 50)
    private MeasurementUnit availableWaterUnit;

    protected SoilProfileEntity() {
        // exigido pelo JPA
    }

    public static SoilProfileEntity newInstance() {
        return new SoilProfileEntity();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSoilType() { return soilType; }
    public void setSoilType(String soilType) { this.soilType = soilType; }
    public BigDecimal getFieldCapacity() { return fieldCapacity; }
    public void setFieldCapacity(BigDecimal fieldCapacity) { this.fieldCapacity = fieldCapacity; }
    public BigDecimal getPermanentWiltingPoint() { return permanentWiltingPoint; }
    public void setPermanentWiltingPoint(BigDecimal permanentWiltingPoint) { this.permanentWiltingPoint = permanentWiltingPoint; }
    public BigDecimal getBulkDensity() { return bulkDensity; }
    public void setBulkDensity(BigDecimal bulkDensity) { this.bulkDensity = bulkDensity; }
    public BigDecimal getEffectiveRootDepth() { return effectiveRootDepth; }
    public void setEffectiveRootDepth(BigDecimal effectiveRootDepth) { this.effectiveRootDepth = effectiveRootDepth; }
    public BigDecimal getAvailableWater() { return availableWater; }
    public void setAvailableWater(BigDecimal availableWater) { this.availableWater = availableWater; }
    public MeasurementUnit getSoilWaterUnit() { return soilWaterUnit; }
    public void setSoilWaterUnit(MeasurementUnit soilWaterUnit) { this.soilWaterUnit = soilWaterUnit; }
    public MeasurementUnit getBulkDensityUnit() { return bulkDensityUnit; }
    public void setBulkDensityUnit(MeasurementUnit bulkDensityUnit) { this.bulkDensityUnit = bulkDensityUnit; }
    public MeasurementUnit getRootDepthUnit() { return rootDepthUnit; }
    public void setRootDepthUnit(MeasurementUnit rootDepthUnit) { this.rootDepthUnit = rootDepthUnit; }
    public MeasurementUnit getAvailableWaterUnit() { return availableWaterUnit; }
    public void setAvailableWaterUnit(MeasurementUnit availableWaterUnit) { this.availableWaterUnit = availableWaterUnit; }
}
