package com.nodotextil.trazatex.quality.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** El driver de PostgreSQL enlaza {@code OffsetDateTime} (no {@code Instant}) con columnas {@code timestamptz}. */
final class Timestamps {

    private Timestamps() {
    }

    static OffsetDateTime toDb(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant fromDb(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
