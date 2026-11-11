package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code crops}.
 */
@Entity
@Table(name = "crops")
public class CropEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "variety", length = 255)
    private String variety;

    @Column(name = "cultivation_cycle_days")
    private Integer cultivationCycleDays;

    @OneToMany(mappedBy = "crop", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    private List<CropStageEntity> stages = new ArrayList<>();

    protected CropEntity() {
        // exigido pelo JPA
    }

    public static CropEntity newInstance() {
        return new CropEntity();
    }

    public void addStage(CropStageEntity stage) {
        stage.setCrop(this);
        stages.add(stage);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }
    public Integer getCultivationCycleDays() { return cultivationCycleDays; }
    public void setCultivationCycleDays(Integer cultivationCycleDays) { this.cultivationCycleDays = cultivationCycleDays; }
    public List<CropStageEntity> getStages() { return stages; }
}
