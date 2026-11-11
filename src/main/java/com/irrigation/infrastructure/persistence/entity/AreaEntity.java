package com.irrigation.infrastructure.persistence.entity;

import com.irrigation.domain.model.enums.IrrigationSystemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code areas}. Separado do agregado de domínio
 * {@link com.irrigation.domain.model.Area}.
 */
@Entity
@Table(name = "areas")
public class AreaEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "area_ha", nullable = false)
    private BigDecimal areaHectares;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "general_characteristics", columnDefinition = "text")
    private String generalCharacteristics;

    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_system_type", nullable = false, length = 50)
    private IrrigationSystemType irrigationSystemType;

    @Column(name = "system_flow_rate", nullable = false)
    private BigDecimal systemFlowRate;

    @Column(name = "irrigation_efficiency")
    private BigDecimal irrigationEfficiency;

    protected AreaEntity() {
        // exigido pelo JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAreaHectares() { return areaHectares; }
    public void setAreaHectares(BigDecimal areaHectares) { this.areaHectares = areaHectares; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getGeneralCharacteristics() { return generalCharacteristics; }
    public void setGeneralCharacteristics(String generalCharacteristics) { this.generalCharacteristics = generalCharacteristics; }
    public IrrigationSystemType getIrrigationSystemType() { return irrigationSystemType; }
    public void setIrrigationSystemType(IrrigationSystemType irrigationSystemType) { this.irrigationSystemType = irrigationSystemType; }
    public BigDecimal getSystemFlowRate() { return systemFlowRate; }
    public void setSystemFlowRate(BigDecimal systemFlowRate) { this.systemFlowRate = systemFlowRate; }
    public BigDecimal getIrrigationEfficiency() { return irrigationEfficiency; }
    public void setIrrigationEfficiency(BigDecimal irrigationEfficiency) { this.irrigationEfficiency = irrigationEfficiency; }

    public static AreaEntity newInstance() {
        return new AreaEntity();
    }
}
