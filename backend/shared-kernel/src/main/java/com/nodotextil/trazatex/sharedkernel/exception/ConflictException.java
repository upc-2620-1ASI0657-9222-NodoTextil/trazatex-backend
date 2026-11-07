package com.nodotextil.trazatex.sharedkernel.exception;

/** La operación choca con el estado actual del recurso (transición inválida, duplicado, versión). Se mapea a 409. */
public class ConflictException extends TrazatexException {

    public static final String CODE = "CONFLICT";

    public ConflictException(String message) {
        super(CODE, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
