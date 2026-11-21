package com.nodotextil.trazatex.sharedkernel.exception;

public class ForbiddenException extends TrazatexException {

    public static final String CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(CODE, message);
    }

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
