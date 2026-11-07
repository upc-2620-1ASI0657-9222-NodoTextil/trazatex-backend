package com.nodotextil.trazatex.production.application.port.out;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados; {@code page} empieza en 0. */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

    /** Tamaño por defecto de una página cuando el cliente no pide uno. */
    public static final int DEFAULT_SIZE = 20;
    /** Tope de una página (RNF-03): si piden más, se usa este valor. */
    public static final int MAX_SIZE = 100;

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
