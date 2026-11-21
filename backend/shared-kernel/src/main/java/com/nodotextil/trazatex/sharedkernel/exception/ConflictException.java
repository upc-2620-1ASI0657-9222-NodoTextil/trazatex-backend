package com.nodotextil.trazatex.sharedkernel.exception;

public class ConflictException extends TrazatexException {

    public static final String CODE = "CONFLICT";

    public ConflictException(String message) {
        super(CODE, message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
