package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

/**
 * Paginación (RNF-03): página desde 0, tamaño por defecto {@value Page#DEFAULT_SIZE} y <b>máximo {@value Page#MAX_SIZE}</b>:
 * si piden más, se usa el máximo. Un valor que no tiene sentido (página negativa, tamaño menor que 1) es 400.
 */
final class Paging {

    private Paging() {
    }

    static int page(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new ValidationException(ErrorCodes.INVALID_PAGINATION, "La página debe ser 0 o mayor.", java.util.List.of("page"));
        }
        return page;
    }

    static int size(Integer size) {
        if (size == null) {
            return Page.DEFAULT_SIZE;
        }
        if (size < 1) {
            throw new ValidationException(ErrorCodes.INVALID_PAGINATION, "El tamaño de página debe ser 1 o mayor.", java.util.List.of("size"));
        }
        return Math.min(size, Page.MAX_SIZE);
    }
}
