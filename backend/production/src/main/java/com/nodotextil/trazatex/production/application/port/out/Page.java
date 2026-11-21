package com.nodotextil.trazatex.production.application.port.out;

import java.util.List;
import java.util.function.Function;

public record Page<T>(List<T> items, int page, int size, long totalElements) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public int totalPages() {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
