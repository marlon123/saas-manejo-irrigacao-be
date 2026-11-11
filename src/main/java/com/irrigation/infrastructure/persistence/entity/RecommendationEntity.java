package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code recommendations}.
 *
 * <p>Entrada e resultado são snapshots JSONB tipados, o que torna cada registro
 * autocontido e imune a mudanças futuras de metodologia (Req 5.7).
 */
@Entity
@Table(name = "recommendations")
public class RecommendationEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", nullable = false)
    private CalculationInputJson inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", nullable = false)
    private CalculationResultJson results;

    @Column(name = "methodology_id", nullable = false, length = 100)
    private String methodologyId;

    @Column(name = "methodology_version", nullable = false, length = 50)
    private String methodologyVersion;

    protected RecommendationEntity() {
        // exigido pelo JPA
    }

    public static RecommendationEntity newInstance() {
        return new RecommendationEntity();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public CalculationInputJson getInputData() { return inputData; }
    public void setInputData(CalculationInputJson inputData) { this.inputData = inputData; }
    public CalculationResultJson getResults() { return results; }
    public void setResults(CalculationResultJson results) { this.results = results; }
    public String getMethodologyId() { return methodologyId; }
    public void setMethodologyId(String methodologyId) { this.methodologyId = methodologyId; }
    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }
}
