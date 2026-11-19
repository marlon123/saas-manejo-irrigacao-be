package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.Kc;
import com.irrigation.domain.model.valueobjects.RootDepth;

import java.math.BigDecimal;
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
    private Kc kc;
    private RootDepth rootDepth;

    /**
     * Construtor de compatibilidade: cultura sem Kc e sem profundidade de raízes
     * (como as culturas anteriores ao módulo de balanço hídrico, Req 2.7).
     */
    public Crop(UUID id, String name, String variety, Integer cultivationCycleDays, List<CropStage> stages) {
        this(id, name, variety, cultivationCycleDays, stages, null, null);
    }

    /**
     * Construtor completo (Req 2.1, 2.4). {@code kc} e {@code rootDepth} são opcionais no domínio;
     * a obrigatoriedade na API é tratada na camada de aplicação.
     */
    public Crop(UUID id, String name, String variety, Integer cultivationCycleDays,
                List<CropStage> stages, Kc kc, RootDepth rootDepth) {
        this.id = Objects.requireNonNull(id, "id não pode ser nulo");
        setName(name);
        setVariety(variety);
        this.cultivationCycleDays = cultivationCycleDays;
        setStages(stages);
        setKc(kc);
        setRootDepth(rootDepth);
    }

    /**
     * Atualiza os dados da cultura, aplicando invariantes, e preserva Kc e profundidade de raízes atuais.
     * (Req 2.7, 2.16)
     */
    public void update(String name, String variety, Integer cultivationCycleDays, List<CropStage> newStages) {
        update(name, variety, cultivationCycleDays, newStages, this.kc, this.rootDepth);
    }

    /**
     * Atualiza todos os dados da cultura, incluindo Kc e profundidade de raízes (Req 2.1, 2.4).
     * Nada é alterado se alguma validação falhar.
     */
    public void update(String name, String variety, Integer cultivationCycleDays, List<CropStage> newStages,
                       Kc newKc, RootDepth newRootDepth) {
        validateName(name);
        validateVariety(variety);
        validateStages(newStages);
        validateKc(newKc);
        validateRootDepth(newRootDepth);
        this.name = name;
        this.variety = variety;
        this.cultivationCycleDays = cultivationCycleDays;
        this.stages = new ArrayList<>(newStages);
        this.kc = newKc;
        this.rootDepth = newRootDepth;
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
     * Valida a coleção de estágios (Req 2.5, 2.11): de 0 a 20 estágios, com ordem única.
     * Nulo é tratado como coleção vazia na validação, mas o agregado não aceita lista nula na construção.
     *
     * @throws IllegalArgumentException se a coleção for inválida
     */
    public static void validateStages(List<CropStage> stages) {
        if (stages == null) {
            throw new IllegalArgumentException("a lista de estágios não pode ser nula");
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

    /**
     * Valida o Kc da cultura (Req 2.2): opcional; quando presente, no máximo 2 casas decimais.
     * A faixa (maior que 0 e até 2,0) já é garantida pelo value object {@link Kc}.
     * A regra de escala fica aqui, e não em {@code Kc}, porque o value object é usado nos estágios.
     *
     * @throws IllegalArgumentException se o Kc tiver mais de 2 casas decimais
     */
    public static void validateKc(Kc kc) {
        if (kc != null && hasMoreThanTwoDecimals(kc.value())) {
            throw new IllegalArgumentException("Kc deve ter no máximo 2 casas decimais");
        }
    }

    /**
     * Valida a profundidade efetiva de raízes (Req 2.3): opcional; quando presente, no máximo 2 casas decimais.
     * A faixa (maior que 0 e até 5.000,00 mm) já é garantida pelo value object {@link RootDepth}.
     *
     * @throws IllegalArgumentException se a profundidade tiver mais de 2 casas decimais
     */
    public static void validateRootDepth(RootDepth rootDepth) {
        if (rootDepth != null && hasMoreThanTwoDecimals(rootDepth.value())) {
            throw new IllegalArgumentException("profundidade efetiva de raízes deve ter no máximo 2 casas decimais");
        }
    }

    private static boolean hasMoreThanTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > 2;
    }

    private void setKc(Kc kc) {
        validateKc(kc);
        this.kc = kc;
    }

    private void setRootDepth(RootDepth rootDepth) {
        validateRootDepth(rootDepth);
        this.rootDepth = rootDepth;
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
    /** Kc da cultura; nulo em culturas anteriores ao módulo de balanço hídrico (Req 2.7). */
    public Kc getKc() { return kc; }
    /** Profundidade efetiva de raízes (Z); nula em culturas anteriores ao módulo (Req 2.7). */
    public RootDepth getRootDepth() { return rootDepth; }
    public List<CropStage> getStages() { return Collections.unmodifiableList(stages); }
}
