package com.nodotextil.trazatex.sharedkernel.exception;

import java.util.List;

/**
 * Base de las excepciones de negocio. Lleva un {@code code} estable que termina en {@code ApiError.code};
 * el mapeo a HTTP lo hace el {@code @ControllerAdvice} de cada servicio.
 */
public class TrazatexException extends RuntimeException {

    private final String code;
    private final List<String> details;

    public TrazatexException(String code, String message) {
        this(code, message, List.of());
    }

    public TrazatexException(String code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public String code() {
        return code;
    }

    public List<String> details() {
        return details;
    }
}
