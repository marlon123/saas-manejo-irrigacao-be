package com.irrigation.application.pagination;

import com.irrigation.application.validation.ViolationCollector;
import com.irrigation.domain.repository.PageRequest;

/**
 * Resolve parâmetros de paginação brutos em um {@link PageRequest}.
 *
 * <p>Padrões: página 0, tamanho 20. Página negativa ou tamanho fora de 1..100
 * são rejeitados com violação de validação (Req 1.18, 2.15).
 */
public final class PageParams {

    private PageParams() {
    }

    public static PageRequest resolve(Integer page, Integer size) {
        ViolationCollector v = new ViolationCollector();
        int p = page == null ? PageRequest.DEFAULT_PAGE : page;
        int s = size == null ? PageRequest.DEFAULT_SIZE : size;
        if (p < 0) {
            v.add("page", "deve ser maior ou igual a zero");
        }
        if (s < 1 || s > PageRequest.MAX_SIZE) {
            v.add("size", "deve estar entre 1 e " + PageRequest.MAX_SIZE);
        }
        v.throwIfAny();
        return PageRequest.of(p, s);
    }
}
