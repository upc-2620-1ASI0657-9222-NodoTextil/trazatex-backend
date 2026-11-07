package com.nodotextil.trazatex.organizationaccess.domain.service;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Política de contraseña al crear la cuenta: mínimo {@value #MIN_LENGTH} caracteres, sin reglas de composición (decisión del
 * usuario, fase 02), y confirmación idéntica. El rechazo por contraseña filtrada (HIBP, ADR-007) se aplica aparte, tras
 * esta validación. El máximo de 72 bytes es el límite técnico de BCrypt, que ignoraría el resto.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password, String confirmation) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new ValidationException(ErrorCodes.PASSWORD_TOO_SHORT,
                    "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.", List.of("password"));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ValidationException(ErrorCodes.PASSWORD_TOO_LONG,
                    "La contraseña es demasiado larga (máximo " + MAX_BYTES + " bytes).", List.of("password"));
        }
        if (!password.equals(confirmation)) {
            throw new ValidationException(ErrorCodes.PASSWORD_MISMATCH,
                    "La contraseña y su confirmación no coinciden.", List.of("passwordConfirmation"));
        }
    }
}
