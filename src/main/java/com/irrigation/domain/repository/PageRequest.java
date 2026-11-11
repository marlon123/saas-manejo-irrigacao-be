package com.irrigation.domain.repository;

/**
 * Value object para parâmetros de paginação.
 * (Req 1.3–1.5, 2.3–2.4, 3.3, 6.3 — página padrão 0, tamanho padrão 20, máximo 100)
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("página deve ser maior ou igual a zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("tamanho da página deve estar entre 1 e " + MAX_SIZE);
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest ofDefaults() {
        return new PageRequest(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    /**
     * Retorna o offset (primeiro índice da página).
     */
    public long offset() {
        return (long) page * size;
    }
}
