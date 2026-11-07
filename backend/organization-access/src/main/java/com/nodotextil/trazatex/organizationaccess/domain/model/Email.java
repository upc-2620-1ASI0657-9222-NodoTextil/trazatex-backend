package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Correo normalizado (sin espacios, en minúsculas): la unicidad de usuarios e invitaciones es insensible a mayúsculas. */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        value = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new ValidationException(ErrorCodes.EMAIL_INVALID, "El correo electrónico no es válido.", List.of());
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
