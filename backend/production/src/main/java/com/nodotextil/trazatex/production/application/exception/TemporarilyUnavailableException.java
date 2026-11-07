package com.nodotextil.trazatex.production.application.exception;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;

/**
 * Una carrera con otra operación (conflicto de versión, clave duplicada, espera de un bloqueo) no se resolvió tras los
 * reintentos. No es un rechazo de negocio: el cliente repite el mismo comando (503 con {@code Retry-After}).
 */
public class TemporarilyUnavailableException extends RuntimeException {

    public TemporarilyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public String code() {
        return ErrorCodes.TEMPORARILY_UNAVAILABLE;
    }
}
