package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.random.RandomGenerator;

/**
 * Identificador interno del lote: {@code TT-} + {@value #RANDOM_LENGTH} caracteres aleatorios Crockford Base32 (50
 * bits). Único y nunca cambia, ni al transferir el lote. La unicidad la garantiza la base (restricción {@code UNIQUE}).
 */
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

    /** Lee un Traceability ID escrito por una persona (minúsculas, {@code O} por {@code 0}...). */
    public static TraceabilityId parse(String text) {
        if (text == null) {
            throw new ValidationException(ErrorCodes.INVALID_CODE, "El Traceability ID es obligatorio.", null);
        }
        return new TraceabilityId(Crockford32.normalize(text));
    }

    /**
     * Prefijo escrito por una persona para buscar (mayúsculas, O→0, I/L→1). No se valida: un prefijo con letras que
     * ningún código contiene (como la U) simplemente no encuentra nada.
     */
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
