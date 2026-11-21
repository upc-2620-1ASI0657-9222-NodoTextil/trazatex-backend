package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.random.RandomGenerator;

public record QrCode(String value) {

    public static final int LENGTH = 26;

    public QrCode {
        if (!Crockford32.isValid(value, LENGTH)) {
            throw new ValidationException(ErrorCodes.INVALID_CODE,
                    "El código QR debe tener " + LENGTH + " caracteres Crockford Base32.", null);
        }
    }

    public static QrCode generate(RandomGenerator random) {
        return new QrCode(Crockford32.generate(LENGTH, random));
    }

    public static QrCode parse(String text) {
        if (text == null) {
            throw new ValidationException(ErrorCodes.INVALID_CODE, "El código QR es obligatorio.", null);
        }
        return new QrCode(Crockford32.normalize(text));
    }

    public static boolean isValid(String value) {
        return Crockford32.isValid(value, LENGTH);
    }

    @Override
    public String toString() {
        return value;
    }
}
