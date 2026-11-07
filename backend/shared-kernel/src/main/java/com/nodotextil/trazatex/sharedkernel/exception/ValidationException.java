package com.nodotextil.trazatex.sharedkernel.exception;

import java.util.List;

/** Datos de entrada inválidos o regla de negocio incumplida. Se mapea a 400/422 según el servicio. */
public class ValidationException extends TrazatexException {

    public static final String CODE = "VALIDATION_ERROR";

    public ValidationException(String message) {
        super(CODE, message);
    }

    public ValidationException(String message, List<String> details) {
        super(CODE, message, details);
    }

    public ValidationException(String code, String message, List<String> details) {
        super(code, message, details);
    }
}
