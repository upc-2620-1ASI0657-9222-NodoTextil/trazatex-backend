package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Acumula los datos obligatorios que faltan para responder con la lista completa (US12: "datos obligatorios"). */
public final class RequiredFields {

    private final List<String> missing = new ArrayList<>();

    public RequiredFields text(String name, String value) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
        return this;
    }

    public RequiredFields value(String name, Object value) {
        if (value == null) {
            missing.add(name);
        }
        return this;
    }

    public RequiredFields notEmpty(String name, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            missing.add(name);
        }
        return this;
    }

    public void verify() {
        if (!missing.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS,
                    "Faltan datos obligatorios: " + String.join(", ", missing) + ".", missing);
        }
    }
}
