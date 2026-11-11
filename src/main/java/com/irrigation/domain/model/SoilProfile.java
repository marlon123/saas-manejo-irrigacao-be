package com.irrigation.domain.model;

import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representando parâmetros de solo.
 * (Req 3.10–3.14) — invariantes aplicados na construção.
 *
 * <p>Cada parâmetro carrega uma unidade de medida explícita (Req 3.14).
 */
public class SoilProfile {

    private final UUID id;
    private String soilType;
    private VolumetricWaterContent fieldCapacity;
    private VolumetricWaterContent permanentWiltingPoint;
    private BulkDensity bulkDensity;
    private RootDepth effectiveRootDepth;
    private AvailableWater availableWater;
    private MeasurementUnit soilWaterUnit;
    private MeasurementUnit bulkDensityUnit;
    private MeasurementUnit rootDepthUnit;
    private MeasurementUnit availableWaterUnit;

    public SoilProfile(
            UUID id,
            String soilType,
            VolumetricWaterContent fieldCapacity,
            VolumetricWaterContent permanentWiltingPoint,
            BulkDensity bulkDensity,
            RootDepth effectiveRootDepth,
            AvailableWater availableWater,
            MeasurementUnit soilWaterUnit,
            MeasurementUnit bulkDensityUnit,
            MeasurementUnit rootDepthUnit,
            MeasurementUnit availableWaterUnit) {

        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        setSoilType(soilType);
        setWaterContents(fieldCapacity, permanentWiltingPoint);
        this.bulkDensity = Objects.requireNonNull(bulkDensity, "densidade aparente não pode ser nula");
        this.effectiveRootDepth = Objects.requireNonNull(effectiveRootDepth, "profundidade de raízes não pode ser nula");
        this.availableWater = Objects.requireNonNull(availableWater, "água disponível não pode ser nula");
        this.soilWaterUnit = Objects.requireNonNull(soilWaterUnit, "unidade de água do solo não pode ser nula");
        this.bulkDensityUnit = Objects.requireNonNull(bulkDensityUnit, "unidade de densidade aparente não pode ser nula");
        this.rootDepthUnit = Objects.requireNonNull(rootDepthUnit, "unidade de profundidade de raízes não pode ser nula");
        this.availableWaterUnit = Objects.requireNonNull(availableWaterUnit, "unidade de água disponível não pode ser nula");
    }

    /**
     * Atualiza os dados do perfil de solo, aplicando invariantes.
     * (Req 3.6, 3.15)
     */
    public void update(
            String soilType,
            VolumetricWaterContent fieldCapacity,
            VolumetricWaterContent permanentWiltingPoint,
            BulkDensity bulkDensity,
            RootDepth effectiveRootDepth,
            AvailableWater availableWater,
            MeasurementUnit soilWaterUnit,
            MeasurementUnit bulkDensityUnit,
            MeasurementUnit rootDepthUnit,
            MeasurementUnit availableWaterUnit) {

        setSoilType(soilType);
        setWaterContents(fieldCapacity, permanentWiltingPoint);
        this.bulkDensity = Objects.requireNonNull(bulkDensity, "densidade aparente não pode ser nula");
        this.effectiveRootDepth = Objects.requireNonNull(effectiveRootDepth, "profundidade de raízes não pode ser nula");
        this.availableWater = Objects.requireNonNull(availableWater, "água disponível não pode ser nula");
        this.soilWaterUnit = Objects.requireNonNull(soilWaterUnit, "unidade de água do solo não pode ser nula");
        this.bulkDensityUnit = Objects.requireNonNull(bulkDensityUnit, "unidade de densidade aparente não pode ser nula");
        this.rootDepthUnit = Objects.requireNonNull(rootDepthUnit, "unidade de profundidade de raízes não pode ser nula");
        this.availableWaterUnit = Objects.requireNonNull(availableWaterUnit, "unidade de água disponível não pode ser nula");
    }

    /**
     * Valida o tipo de solo: obrigatório e não em branco.
     *
     * @throws IllegalArgumentException se o tipo de solo for inválido
     */
    public static void validateSoilType(String soilType) {
        if (soilType == null) {
            throw new IllegalArgumentException("tipo de solo é obrigatório");
        }
        if (soilType.isBlank()) {
            throw new IllegalArgumentException("tipo de solo não pode ser em branco");
        }
    }

    /**
     * Valida que a capacidade de campo é estritamente maior que o ponto de murcha permanente (Req 3.11).
     *
     * @throws IllegalArgumentException se a relação não for respeitada
     */
    public static void validateWaterContents(VolumetricWaterContent fieldCapacity, VolumetricWaterContent permanentWiltingPoint) {
        Objects.requireNonNull(fieldCapacity, "capacidade de campo não pode ser nula");
        Objects.requireNonNull(permanentWiltingPoint, "ponto de murcha permanente não pode ser nulo");
        if (fieldCapacity.value().compareTo(permanentWiltingPoint.value()) <= 0) {
            throw new IllegalArgumentException("capacidade de campo deve ser maior que o ponto de murcha permanente");
        }
    }

    private void setSoilType(String soilType) {
        validateSoilType(soilType);
        this.soilType = soilType;
    }

    private void setWaterContents(VolumetricWaterContent fieldCapacity, VolumetricWaterContent permanentWiltingPoint) {
        validateWaterContents(fieldCapacity, permanentWiltingPoint);
        this.fieldCapacity = fieldCapacity;
        this.permanentWiltingPoint = permanentWiltingPoint;
    }

    public UUID getId() { return id; }
    public String getSoilType() { return soilType; }
    public VolumetricWaterContent getFieldCapacity() { return fieldCapacity; }
    public VolumetricWaterContent getPermanentWiltingPoint() { return permanentWiltingPoint; }
    public BulkDensity getBulkDensity() { return bulkDensity; }
    public RootDepth getEffectiveRootDepth() { return effectiveRootDepth; }
    public AvailableWater getAvailableWater() { return availableWater; }
    public MeasurementUnit getSoilWaterUnit() { return soilWaterUnit; }
    public MeasurementUnit getBulkDensityUnit() { return bulkDensityUnit; }
    public MeasurementUnit getRootDepthUnit() { return rootDepthUnit; }
    public MeasurementUnit getAvailableWaterUnit() { return availableWaterUnit; }
}
