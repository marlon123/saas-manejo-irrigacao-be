package com.irrigation.application.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Resultado paginado (Req 6.3): página (base 0), tamanho, total de elementos e total de páginas.
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PagedResult<>(List.copyOf(content), page, size, totalElements, totalPages);
    }

    public <R> PagedResult<R> map(Function<T, R> mapper) {
        return new PagedResult<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
