package com.nodotextil.trazatex.traceability.domain.model;

import java.util.Locale;

public final class TraceabilityIdQuery {

    private TraceabilityIdQuery() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim().toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
    }
}
