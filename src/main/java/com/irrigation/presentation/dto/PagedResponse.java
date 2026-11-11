package com.irrigation.presentation.dto;

import com.irrigation.application.pagination.PagedResult;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de coleções paginadas (Req 6.3): conteúdo mais número da página (base 0),
 * tamanho, total de elementos e total de páginas.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PagedResponse<T> from(PagedResult<S> result, Function<S, T> mapper) {
        return new PagedResponse<>(
                result.content().stream().map(mapper).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
