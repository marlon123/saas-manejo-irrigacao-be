package com.irrigation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modelo de persistência da tabela {@code crop_stages}.
 */
@Entity
@Table(name = "crop_stages")
public class CropStageEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropEntity crop;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "kc", nullable = false)
    private BigDecimal kc;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    protected CropStageEntity() {
        // exigido pelo JPA
    }

    public static CropStageEntity newInstance() {
        return new CropStageEntity();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CropEntity getCrop() { return crop; }
    void setCrop(CropEntity crop) { this.crop = crop; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getKc() { return kc; }
    public void setKc(BigDecimal kc) { this.kc = kc; }
    public int getStageOrder() { return stageOrder; }
    public void setStageOrder(int stageOrder) { this.stageOrder = stageOrder; }
}
