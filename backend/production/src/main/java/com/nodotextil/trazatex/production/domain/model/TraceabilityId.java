package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.random.RandomGenerator;

public record TraceabilityId(String value) {

    public static final String PREFIX = "TT-";
    public static final int RANDOM_LENGTH = 10;

    public TraceabilityId {
        if (!isValid(value)) {
            throw new ValidationException(ErrorCodes.INVALID_CODE,
                    "El Traceability ID debe ser \"" + PREFIX + "\" seguido de " + RANDOM_LENGTH + " caracteres Crockford Base32.", null);
        }
    }

    public static TraceabilityId generate(RandomGenerator random) {
        return new TraceabilityId(PREFIX + Crockford32.generate(RANDOM_LENGTH, random));
    }

    public static TraceabilityId parse(String text) {
        if (text == null) {
            throw new ValidationException(ErrorCodes.INVALID_CODE, "El Traceability ID es obligatorio.", null);
        }
        return new TraceabilityId(Crockford32.normalize(text));
    }

    public static String normalizePrefix(String text) {
        return text == null ? "" : Crockford32.normalize(text);
    }

    public static boolean isValid(String value) {
        return value != null && value.startsWith(PREFIX) && Crockford32.isValid(value.substring(PREFIX.length()), RANDOM_LENGTH);
    }

    @Override
    public String toString() {
        return value;
    }
}
