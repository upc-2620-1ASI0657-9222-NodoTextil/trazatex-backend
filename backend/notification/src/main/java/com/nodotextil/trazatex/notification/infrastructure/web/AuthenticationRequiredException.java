package com.nodotextil.trazatex.notification.infrastructure.web;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

/** Faltan o son inválidas las cabeceras de identidad que inyecta el Gateway. Se mapea a 401. */
class AuthenticationRequiredException extends TrazatexException {
    static final String CODE = "AUTHENTICATION_REQUIRED";
    AuthenticationRequiredException() { super(CODE, "La sesión no es válida."); }
}
