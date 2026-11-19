package com.irrigation.domain.model;

import com.irrigation.domain.model.enums.MeasurementUnit;
import com.irrigation.domain.model.valueobjects.AvailableWater;
import com.irrigation.domain.model.valueobjects.BulkDensity;
import com.irrigation.domain.model.valueobjects.GravimetricWaterContent;
import com.irrigation.domain.model.valueobjects.RootDepth;
import com.irrigation.domain.model.valueobjects.VolumetricWaterContent;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representando parâmetros de solo.
 * (Req 3.10–3.14 da spec original; Req 1 do módulo de balanço hídrico) — invariantes aplicados na construção.
 *
 * <p>Obrigatórios: identificador, tipo de solo e densidade aparente. Os demais campos são opcionais:
 * <ul>
 *   <li>par volumétrico (capacidade de campo e ponto de murcha): "ambos ou nenhum", com CC &gt; PMP quando presente;</li>
 *   <li>par gravimétrico (CC e PMP em % de massa): mesma regra "ambos ou nenhum" e CC &gt; PMP (A-07);</li>
 *   <li>profundidade efetiva de raízes, água disponível e as quatro unidades associadas.</li>
 * </ul>
 * Cada parâmetro informado carrega a unidade de medida do cadastro (Req 3.14 da spec original).
 */
public class SoilProfile {

    private final UUID id;
    private String soilType;
    private VolumetricWaterContent fieldCapacity;
    private VolumetricWaterContent permanentWiltingPoint;
    private GravimetricWaterContent fieldCapacityGravimetric;
    private GravimetricWaterContent permanentWiltingPointGravimetric;
    private BulkDensity bulkDensity;
    private RootDepth effectiveRootDepth;
    private AvailableWater availableWater;
    private MeasurementUnit soilWaterUnit;
    private MeasurementUnit bulkDensityUnit;
    private MeasurementUnit rootDepthUnit;
    private MeasurementUnit availableWaterUnit;

    /**
     * Construtor completo, com o par gravimétrico (Req 1.1, 1.6).
     * Os campos opcionais podem ser nulos.
     */
    public SoilProfile(
            UUID id,
            String soilType,
            VolumetricWaterContent fieldCapacity,
            VolumetricWaterContent permanentWiltingPoint,
            GravimetricWaterContent fieldCapacityGravimetric,
            GravimetricWaterContent permanentWiltingPointGravimetric,
            BulkDensity bulkDensity,
            RootDepth effectiveRootDepth,
            AvailableWater availableWater,
            MeasurementUnit soilWaterUnit,
            MeasurementUnit bulkDensityUnit,
            MeasurementUnit rootDepthUnit,
            MeasurementUnit availableWaterUnit) {

        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        apply(soilType, fieldCapacity, permanentWiltingPoint, fieldCapacityGravimetric,
                permanentWiltingPointGravimetric, bulkDensity, effectiveRootDepth, availableWater,
                soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }

    /**
     * Construtor de compatibilidade (assinatura anterior ao módulo de balanço hídrico):
     * sem o par gravimétrico, que fica nulo.
     */
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
        this(id, soilType, fieldCapacity, permanentWiltingPoint, null, null, bulkDensity, effectiveRootDepth,
                availableWater, soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }

    /**
     * Atualiza os dados do perfil de solo, aplicando invariantes. Todos os campos são substituídos.
     * (Req 3.6, 3.15 da spec original; Req 1.5)
     */
    public void update(
            String soilType,
            VolumetricWaterContent fieldCapacity,
            VolumetricWaterContent permanentWiltingPoint,
            GravimetricWaterContent fieldCapacityGravimetric,
            GravimetricWaterContent permanentWiltingPointGravimetric,
            BulkDensity bulkDensity,
            RootDepth effectiveRootDepth,
            AvailableWater availableWater,
            MeasurementUnit soilWaterUnit,
            MeasurementUnit bulkDensityUnit,
            MeasurementUnit rootDepthUnit,
            MeasurementUnit availableWaterUnit) {
        apply(soilType, fieldCapacity, permanentWiltingPoint, fieldCapacityGravimetric,
                permanentWiltingPointGravimetric, bulkDensity, effectiveRootDepth, availableWater,
                soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
    }

    /**
     * Atualização de compatibilidade (assinatura anterior): zera o par gravimétrico.
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
        update(soilType, fieldCapacity, permanentWiltingPoint, null, null, bulkDensity, effectiveRootDepth,
                availableWater, soilWaterUnit, bulkDensityUnit, rootDepthUnit, availableWaterUnit);
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
     * Valida o par volumétrico (Req 1.7, 1.8): ambos nulos é válido; apenas um informado é par incompleto;
     * quando ambos presentes, a capacidade de campo deve ser estritamente maior que o ponto de murcha.
     *
     * <p>Com um valor nulo e outro presente, a mensagem cita o campo ausente, para o serviço indicar o par
     * incompleto nos dois campos.
     *
     * @throws IllegalArgumentException se o par estiver incompleto ou a relação não for respeitada
     */
    public static void validateWaterContents(VolumetricWaterContent fieldCapacity, VolumetricWaterContent permanentWiltingPoint) {
        validatePair(
                fieldCapacity == null ? null : fieldCapacity.value(),
                permanentWiltingPoint == null ? null : permanentWiltingPoint.value(),
                "capacidade de campo", "ponto de murcha permanente");
    }

    /**
     * Valida o par gravimétrico (Req 1.2, A-07): "ambos ou nenhum" e CC estritamente maior que PMP.
     *
     * @throws IllegalArgumentException se o par estiver incompleto ou a relação não for respeitada
     */
    public static void validateGravimetricContents(
            GravimetricWaterContent fieldCapacityGravimetric,
            GravimetricWaterContent permanentWiltingPointGravimetric) {
        validatePair(
                fieldCapacityGravimetric == null ? null : fieldCapacityGravimetric.value(),
                permanentWiltingPointGravimetric == null ? null : permanentWiltingPointGravimetric.value(),
                "capacidade de campo gravimétrica", "ponto de murcha permanente gravimétrico");
    }

    private static void validatePair(BigDecimal fieldCapacity, BigDecimal wiltingPoint,
                                     String fieldCapacityLabel, String wiltingPointLabel) {
        if (fieldCapacity == null && wiltingPoint == null) {
            return;
        }
        if (fieldCapacity == null) {
            throw new IllegalArgumentException(
                    "par incompleto: " + wiltingPointLabel + " informado sem " + fieldCapacityLabel);
        }
        if (wiltingPoint == null) {
            throw new IllegalArgumentException(
                    "par incompleto: " + fieldCapacityLabel + " informada sem " + wiltingPointLabel);
        }
        if (fieldCapacity.compareTo(wiltingPoint) <= 0) {
            throw new IllegalArgumentException(fieldCapacityLabel + " deve ser maior que o " + wiltingPointLabel);
        }
    }

    private void apply(
            String soilType,
            VolumetricWaterContent fieldCapacity,
            VolumetricWaterContent permanentWiltingPoint,
            GravimetricWaterContent fieldCapacityGravimetric,
            GravimetricWaterContent permanentWiltingPointGravimetric,
            BulkDensity bulkDensity,
            RootDepth effectiveRootDepth,
            AvailableWater availableWater,
            MeasurementUnit soilWaterUnit,
            MeasurementUnit bulkDensityUnit,
            MeasurementUnit rootDepthUnit,
            MeasurementUnit availableWaterUnit) {
        validateSoilType(soilType);
        validateWaterContents(fieldCapacity, permanentWiltingPoint);
        validateGravimetricContents(fieldCapacityGravimetric, permanentWiltingPointGravimetric);
        Objects.requireNonNull(bulkDensity, "densidade aparente não pode ser nula");

        this.soilType = soilType;
        this.fieldCapacity = fieldCapacity;
        this.permanentWiltingPoint = permanentWiltingPoint;
        this.fieldCapacityGravimetric = fieldCapacityGravimetric;
        this.permanentWiltingPointGravimetric = permanentWiltingPointGravimetric;
        this.bulkDensity = bulkDensity;
        this.effectiveRootDepth = effectiveRootDepth;
        this.availableWater = availableWater;
        this.soilWaterUnit = soilWaterUnit;
        this.bulkDensityUnit = bulkDensityUnit;
        this.rootDepthUnit = rootDepthUnit;
        this.availableWaterUnit = availableWaterUnit;
    }

    public UUID getId() { return id; }
    public String getSoilType() { return soilType; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public VolumetricWaterContent getFieldCapacity() { return fieldCapacity; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public VolumetricWaterContent getPermanentWiltingPoint() { return permanentWiltingPoint; }
    /** Opcional: nulo em registros anteriores ao módulo (Req 1.11). */
    public GravimetricWaterContent getFieldCapacityGravimetric() { return fieldCapacityGravimetric; }
    /** Opcional: nulo em registros anteriores ao módulo (Req 1.11). */
    public GravimetricWaterContent getPermanentWiltingPointGravimetric() { return permanentWiltingPointGravimetric; }
    public BulkDensity getBulkDensity() { return bulkDensity; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public RootDepth getEffectiveRootDepth() { return effectiveRootDepth; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public AvailableWater getAvailableWater() { return availableWater; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public MeasurementUnit getSoilWaterUnit() { return soilWaterUnit; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public MeasurementUnit getBulkDensityUnit() { return bulkDensityUnit; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public MeasurementUnit getRootDepthUnit() { return rootDepthUnit; }
    /** Opcional (Req 1.6): pode ser nulo. */
    public MeasurementUnit getAvailableWaterUnit() { return availableWaterUnit; }
}
