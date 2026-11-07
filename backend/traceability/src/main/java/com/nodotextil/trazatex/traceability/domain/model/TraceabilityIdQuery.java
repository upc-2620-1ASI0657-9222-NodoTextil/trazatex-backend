package com.nodotextil.trazatex.traceability.domain.model;

import java.util.Locale;

/**
 * Lo escrito a mano de un Traceability ID o un QR (Crockford Base32, sin {@code O}, {@code I} ni {@code L}): se pasa a mayúsculas y
 * las letras que se confunden se corrigen ({@code O}→{@code 0}, {@code I} y {@code L}→{@code 1}), igual que en Production.
 */
public final class TraceabilityIdQuery {

    private TraceabilityIdQuery() {
    }

    /** @return el texto normalizado, o {@code null} si no queda nada que buscar */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim().toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
    }
}
