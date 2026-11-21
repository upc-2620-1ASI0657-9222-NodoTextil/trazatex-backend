package com.nodotextil.trazatex.sharedkernel.exception;

public class NotFoundException extends TrazatexException {

    public static final String CODE = "NOT_FOUND";

    public NotFoundException(String message) {
        super(CODE, message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
