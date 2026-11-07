package com.nodotextil.trazatex.analytics.domain;

import com.nodotextil.trazatex.analytics.domain.model.ReportingPeriod;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportingPeriodTest {
    @Test void bothDatesAreInclusiveAndMeasuredInLima() {
        var period = ReportingPeriod.parse("2026-10-01", "2026-10-31");
        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test void oneDayIsAValidPeriod() {
        assertThat(ReportingPeriod.parse("2026-10-05", "2026-10-05").endExclusive()).isEqualTo(Instant.parse("2026-10-06T05:00:00Z"));
    }

    @Test void theLimitIs366InclusiveDaysAndNotOneMore() {
        assertThat(ReportingPeriod.parse("2025-01-01", "2026-01-01")).isNotNull();            // 366 días
        assertThat(ReportingPeriod.parse("2024-01-01", "2024-12-31")).isNotNull();            // un año bisiesto completo, 366 días
        assertInvalid("2025-01-01", "2026-01-02");                                            // 367
    }

    @ParameterizedTest @NullAndEmptySource
    void aMissingParameterIsInvalid(String missing) {
        assertInvalid(missing, "2026-10-31");
        assertInvalid("2026-10-01", missing);
        assertInvalid(missing, missing);
    }

    @ParameterizedTest @CsvSource({"2026-13-01,2026-10-31", "2026-10-01,31/10/2026", "ayer,hoy", "2026-02-30,2026-03-01", "'2026-10-01 ',2026-10-0x"})
    void aMalformedDateIsInvalid(String from, String to) { assertInvalid(from, to); }

    @Test void theEndCannotBeBeforeTheStart() { assertInvalid("2026-10-31", "2026-10-01"); }

    private static void assertInvalid(String from, String to) {
        assertThatThrownBy(() -> ReportingPeriod.parse(from, to)).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PERIOD"));
    }
}
