package com.nodotextil.trazatex.organizationaccess.infrastructure.web.security;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

public class AuthenticationRequiredException extends TrazatexException {

    public static final String CODE = "AUTHENTICATION_REQUIRED";

    public AuthenticationRequiredException(String message) {
        super(CODE, message);
    }
}
