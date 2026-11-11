package com.irrigation.domain.model;

import com.irrigation.domain.model.enums.IrrigationSystemType;
import com.irrigation.domain.model.valueobjects.AreaInHectares;
import com.irrigation.domain.model.valueobjects.FlowRate;
import com.irrigation.domain.model.valueobjects.GeographicLocation;
import com.irrigation.domain.model.valueobjects.IrrigationEfficiency;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representando uma área de cultivo.
 * (Req 1.12–1.16) — invariantes aplicados na construção.
 */
public class Area {

    private final UUID id;
    private String name;
    private AreaInHectares areaHectares;
    private GeographicLocation geographicLocation;
    private String generalCharacteristics;
    private IrrigationSystemType irrigationSystemType;
    private FlowRate systemFlowRate;
    private IrrigationEfficiency irrigationEfficiency;

    public Area(
            UUID id,
            String name,
            AreaInHectares areaHectares,
            GeographicLocation geographicLocation,
            String generalCharacteristics,
            IrrigationSystemType irrigationSystemType,
            FlowRate systemFlowRate,
            IrrigationEfficiency irrigationEfficiency) {

        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        setName(name);
        this.areaHectares = Objects.requireNonNull(areaHectares, "área em hectares não pode ser nula");
        this.geographicLocation = geographicLocation;
        this.generalCharacteristics = generalCharacteristics;
        this.irrigationSystemType = Objects.requireNonNull(irrigationSystemType, "tipo de sistema de irrigação não pode ser nulo");
        this.systemFlowRate = Objects.requireNonNull(systemFlowRate, "vazão do sistema não pode ser nula");
        this.irrigationEfficiency = irrigationEfficiency;
    }

    /**
     * Atualiza os dados da área, aplicando invariantes.
     * (Req 1.8, 1.2)
     */
    public void update(String name, AreaInHectares areaHectares, GeographicLocation geographicLocation,
                        String generalCharacteristics, IrrigationSystemType irrigationSystemType,
                        FlowRate systemFlowRate, IrrigationEfficiency irrigationEfficiency) {
        setName(name);
        this.areaHectares = Objects.requireNonNull(areaHectares, "área em hectares não pode ser nula");
        this.geographicLocation = geographicLocation;
        this.generalCharacteristics = generalCharacteristics;
        this.irrigationSystemType = Objects.requireNonNull(irrigationSystemType, "tipo de sistema de irrigação não pode ser nulo");
        this.systemFlowRate = Objects.requireNonNull(systemFlowRate, "vazão do sistema não pode ser nula");
        this.irrigationEfficiency = irrigationEfficiency;
    }

    /**
     * Valida o nome da área (Req 1.13): obrigatório, não em branco e com até 255 caracteres.
     * Exposto para que a camada de aplicação reporte este erro junto com os demais campos.
     *
     * @throws IllegalArgumentException se o nome for inválido
     */
    public static void validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("nome não pode ser em branco");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("nome deve ter no máximo 255 caracteres");
        }
    }

    private void setName(String name) {
        validateName(name);
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public AreaInHectares getAreaHectares() { return areaHectares; }
    public GeographicLocation getGeographicLocation() { return geographicLocation; }
    public String getGeneralCharacteristics() { return generalCharacteristics; }
    public IrrigationSystemType getIrrigationSystemType() { return irrigationSystemType; }
    public FlowRate getSystemFlowRate() { return systemFlowRate; }
    public IrrigationEfficiency getIrrigationEfficiency() { return irrigationEfficiency; }
}
