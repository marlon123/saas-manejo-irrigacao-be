package com.irrigation.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root representando uma cultura agrícola com seus estágios de desenvolvimento.
 * (Req 2.11–2.14) — invariantes aplicados na construção.
 */
public class Crop {

    private final UUID id;
    private String name;
    private String variety;
    private Integer cultivationCycleDays;
    private List<CropStage> stages;

    public Crop(UUID id, String name, String variety, Integer cultivationCycleDays, List<CropStage> stages) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        setName(name);
        setVariety(variety);
        this.cultivationCycleDays = cultivationCycleDays;
        setStages(stages);
    }

    /**
     * Atualiza os dados da cultura, aplicando invariantes.
     * (Req 2.7, 2.16)
     */
    public void update(String name, String variety, Integer cultivationCycleDays, List<CropStage> newStages) {
        setName(name);
        setVariety(variety);
        this.cultivationCycleDays = cultivationCycleDays;
        setStages(newStages);
    }

    /**
     * Valida o nome da cultura (Req 2.14): obrigatório, não em branco e com até 255 caracteres.
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

    /**
     * Valida a variedade (Req 2.11): opcional, com até 255 caracteres.
     *
     * @throws IllegalArgumentException se a variedade for longa demais
     */
    public static void validateVariety(String variety) {
        if (variety != null && variety.length() > 255) {
            throw new IllegalArgumentException("variedade deve ter no máximo 255 caracteres");
        }
    }

    /**
     * Valida a coleção de estágios (Req 2.11, 2.12): de 1 a 20 estágios, com ordem única.
     *
     * @throws IllegalArgumentException se a coleção for inválida
     */
    public static void validateStages(List<CropStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("a cultura deve conter pelo menos um estágio");
        }
        if (stages.size() > 20) {
            throw new IllegalArgumentException("a cultura deve conter no máximo 20 estágios");
        }
        Set<Integer> orders = new HashSet<>();
        for (CropStage stage : stages) {
            if (!orders.add(stage.getStageOrder())) {
                throw new IllegalArgumentException("ordem do estágio deve ser única dentro da cultura: " + stage.getStageOrder());
            }
        }
    }

    private void setName(String name) {
        validateName(name);
        this.name = name;
    }

    private void setVariety(String variety) {
        validateVariety(variety);
        this.variety = variety;
    }

    private void setStages(List<CropStage> stages) {
        validateStages(stages);
        this.stages = new ArrayList<>(stages);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getVariety() { return variety; }
    public Integer getCultivationCycleDays() { return cultivationCycleDays; }
    public List<CropStage> getStages() { return Collections.unmodifiableList(stages); }
}
