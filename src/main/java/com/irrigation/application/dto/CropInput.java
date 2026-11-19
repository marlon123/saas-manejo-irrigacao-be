package com.irrigation.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados de entrada para criar ou atualizar uma cultura (Req 2.1, 2.7 da spec original; Req 2 do módulo
 * de balanço hídrico).
 *
 * <p>{@code kc} é o coeficiente de cultura (adimensional) e {@code rootDepth} é a profundidade radicular Z,
 * em mm. Os dois são obrigatórios na API (Req 2.10). A lista de estágios aceita de 0 a 20 itens (Req 2.4, 2.5).
 */
public record CropInput(
        String name,
        String variety,
        Integer cultivationCycleDays,
        List<CropStageInput> stages,
        BigDecimal kc,
        BigDecimal rootDepth) {

    /**
     * Construtor de compatibilidade (assinatura anterior ao módulo de balanço hídrico): {@code kc} e
     * {@code rootDepth} nulos. Um serviço de cultura recusa esta entrada, porque os dois são obrigatórios.
     */
    public CropInput(String name, String variety, Integer cultivationCycleDays, List<CropStageInput> stages) {
        this(name, variety, cultivationCycleDays, stages, null, null);
    }
}
