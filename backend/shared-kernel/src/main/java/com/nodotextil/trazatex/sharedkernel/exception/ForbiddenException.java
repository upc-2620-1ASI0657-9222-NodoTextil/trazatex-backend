package com.nodotextil.trazatex.sharedkernel.exception;

/** El usuario está autenticado pero no puede realizar la operación (rol, empresa, privacidad). Se mapea a 403. */
public class ForbiddenException extends TrazatexException {

    public static final String CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(CODE, message);
    }

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
