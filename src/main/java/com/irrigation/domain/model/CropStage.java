package com.irrigation.domain.model;

import com.irrigation.domain.model.valueobjects.Kc;

import java.util.Objects;

/**
 * Value object parte do agregado {@link Crop}, representando um estágio
 * de desenvolvimento com coeficiente de cultura (Kc).
 * (Req 2.12 — nome ≤ 100, Kc > 0 e ≤ 2,0, ordem positiva e única dentro da cultura)
 */
public class CropStage {

    private final String name;
    private final Kc kc;
    private final int stageOrder;

    public CropStage(String name, Kc kc, int stageOrder) {
        validateName(name);
        validateStageOrder(stageOrder);
        this.name = name;
        this.kc = Objects.requireNonNull(kc, "Kc não pode ser nulo");
        this.stageOrder = stageOrder;
    }

    /**
     * Valida o nome do estágio (Req 2.12): obrigatório, não em branco e com até 100 caracteres.
     *
     * @throws IllegalArgumentException se o nome for inválido
     */
    public static void validateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("nome do estágio é obrigatório");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("nome do estágio não pode ser em branco");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("nome do estágio deve ter no máximo 100 caracteres");
        }
    }

    /**
     * Valida a ordem do estágio (Req 2.12): inteiro positivo.
     *
     * @throws IllegalArgumentException se a ordem não for positiva
     */
    public static void validateStageOrder(int stageOrder) {
        if (stageOrder <= 0) {
            throw new IllegalArgumentException("ordem do estágio deve ser um inteiro positivo");
        }
    }

    public String getName() { return name; }
    public Kc getKc() { return kc; }
    public int getStageOrder() { return stageOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CropStage that = (CropStage) o;
        return stageOrder == that.stageOrder && Objects.equals(name, that.name) && Objects.equals(kc, that.kc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kc, stageOrder);
    }
}
