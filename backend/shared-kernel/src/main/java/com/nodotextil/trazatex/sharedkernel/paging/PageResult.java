package com.nodotextil.trazatex.sharedkernel.paging;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados, tal como la devuelve la API: {@code {items, page, size, totalElements, totalPages}}. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, PageRequest request, long totalElements) {
        return new PageResult<>(List.copyOf(items), request.page(), request.size(), totalElements,
                (int) ((totalElements + request.size() - 1) / request.size()));
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
