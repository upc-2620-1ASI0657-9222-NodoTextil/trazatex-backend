package com.nodotextil.trazatex.organizationaccess.application.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

/** Correo o contraseña incorrectos, usuario inexistente o inactivo: siempre el mismo mensaje genérico. Se mapea a 401. */
public class InvalidCredentialsException extends TrazatexException {

    public static final String CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(CODE, "Las credenciales no son válidas.");
    }
}
