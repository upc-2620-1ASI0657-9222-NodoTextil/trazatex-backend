package com.nodotextil.trazatex.analytics.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Periodo de una consulta: fechas inclusivas en {@code America/Lima}. {@code from} se toma desde las 00:00 y {@code to} hasta el final de su
 * día. Más de {@link #MAX_DAYS} días, {@code from > to}, un parámetro ausente o una fecha mal escrita son {@code INVALID_PERIOD}.
 */
public record ReportingPeriod(LocalDate from, LocalDate to) {
    public static final String CODE = "INVALID_PERIOD";
    public static final ZoneId ZONE = ZoneId.of("America/Lima");
    public static final int MAX_DAYS = 366;

    public ReportingPeriod {
        if (from == null || to == null) throw invalid("Indica las fechas «from» y «to» (AAAA-MM-DD).");
        if (to.isBefore(from)) throw invalid("La fecha final no puede ser anterior a la inicial.");
        if (from.until(to.plusDays(1), java.time.temporal.ChronoUnit.DAYS) > MAX_DAYS)
            throw invalid("El periodo no puede superar " + MAX_DAYS + " días.");
    }

    public static ReportingPeriod parse(String from, String to) {
        return new ReportingPeriod(date(from, "from"), date(to, "to"));
    }

    private static LocalDate date(String value, String name) {
        if (value == null || value.isBlank()) throw invalid("Falta el parámetro «" + name + "» (AAAA-MM-DD).");
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw invalid("El parámetro «" + name + "» no es una fecha válida (AAAA-MM-DD).");
        }
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(CODE, message, java.util.List.of());
    }

    public Instant startInclusive() { return from.atStartOfDay(ZONE).toInstant(); }
    public Instant endExclusive() { return to.plusDays(1).atStartOfDay(ZONE).toInstant(); }
}
