package com.nodotextil.trazatex.sharedkernel.paging;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.List;

/**
 * Qué página pide el cliente (RNF-03): {@code page} desde 0 (por defecto 0), {@code size} por defecto {@value #DEFAULT_SIZE} y
 * <b>máximo {@value #MAX_SIZE}</b> (si piden más, se usa el máximo y la respuesta trae el tamaño efectivo). Una página negativa o un
 * tamaño menor que 1 no tienen sentido: 400 {@code INVALID_PAGINATION}.
 */
public record PageRequest(int page, int size) {

    public static final String INVALID_PAGINATION = "INVALID_PAGINATION";
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Valida lo que llegó por la URL ({@code null} = no lo pidieron). */
    public static PageRequest of(Integer page, Integer size) {
        if (page != null && page < 0) {
            throw new ValidationException(INVALID_PAGINATION, "La página debe ser 0 o mayor.", List.of("page"));
        }
        if (size != null && size < 1) {
            throw new ValidationException(INVALID_PAGINATION, "El tamaño de página debe ser 1 o mayor.", List.of("size"));
        }
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }

    /** Filas que se saltan ({@code long}: una página enorme no desborda). */
    public long offset() {
        return (long) page * size;
    }
}
