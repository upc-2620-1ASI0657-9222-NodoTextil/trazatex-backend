package com.nodotextil.trazatex.traceability.infrastructure.web.security;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

/** Faltan o son inválidas las cabeceras de identidad que inyecta el Gateway. Se mapea a 401. */
public class AuthenticationRequiredException extends TrazatexException {

    public static final String CODE = "AUTHENTICATION_REQUIRED";

    public AuthenticationRequiredException(String message) {
        super(CODE, message);
    }
}
