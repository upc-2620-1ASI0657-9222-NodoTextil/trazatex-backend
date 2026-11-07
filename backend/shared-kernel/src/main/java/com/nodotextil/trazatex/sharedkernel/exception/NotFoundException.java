package com.nodotextil.trazatex.sharedkernel.exception;

/** El recurso no existe (o no es visible para quien consulta). Se mapea a 404. */
public class NotFoundException extends TrazatexException {

    public static final String CODE = "NOT_FOUND";

    public NotFoundException(String message) {
        super(CODE, message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
