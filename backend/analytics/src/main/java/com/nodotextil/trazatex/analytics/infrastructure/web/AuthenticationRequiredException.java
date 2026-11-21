package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

class AuthenticationRequiredException extends TrazatexException {
    static final String CODE = "AUTHENTICATION_REQUIRED";
    AuthenticationRequiredException() { super(CODE, "La sesión no es válida."); }
}
