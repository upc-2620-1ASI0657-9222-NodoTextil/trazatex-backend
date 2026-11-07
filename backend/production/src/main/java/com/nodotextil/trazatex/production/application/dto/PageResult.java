package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.application.port.out.Page;

import java.util.List;

/** Una página de resultados; {@code size} es el tamaño efectivo (nunca más de {@value Page#MAX_SIZE}). */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.items(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
