package com.nodotextil.trazatex.organizationaccess.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** RNF-06: las reglas del bloqueo, ahora por correo (exista o no la cuenta). */
class LoginAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private final LoginAttempts attempts = LoginAttempts.none(new Email("ana@textil.pe"), NOW);

    @Test
    void startsClean() {
        assertThat(attempts.failedAttempts()).isZero();
        assertThat(attempts.lockedUntil()).isNull();
        assertThat(attempts.isLocked(NOW)).isFalse();
    }

    @Test
    void threeConsecutiveFailuresLockForFifteenMinutes() {
        attempts.recordFailure(NOW);
        attempts.recordFailure(NOW);
        assertThat(attempts.isLocked(NOW)).isFalse();
        assertThat(attempts.failedAttempts()).isEqualTo(2);

        attempts.recordFailure(NOW);

        assertThat(attempts.isLocked(NOW)).isTrue();
        assertThat(attempts.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void lockExpiresAfterFifteenMinutesAndGivesThreeNewAttempts() {
        for (int i = 0; i < 3; i++) {
            attempts.recordFailure(NOW);
        }

        assertThat(attempts.isLocked(NOW.plus(Duration.ofMinutes(14)).plusSeconds(59))).isTrue();
        Instant after = NOW.plus(Duration.ofMinutes(15));
        assertThat(attempts.isLocked(after)).isFalse();

        attempts.recordFailure(after);
        assertThat(attempts.lockedUntil()).as("el bloqueo vencido se descarta al contar de nuevo").isNull();
        assertThat(attempts.failedAttempts()).isEqualTo(1);
        attempts.recordFailure(after);
        assertThat(attempts.isLocked(after)).isFalse();
        attempts.recordFailure(after);
        assertThat(attempts.lockedUntil()).isEqualTo(after.plus(Duration.ofMinutes(15)));
    }

    @Test
    void attemptsDuringTheLockDoNotExtendItNorCount() {
        for (int i = 0; i < 3; i++) {
            attempts.recordFailure(NOW);
        }
        Instant lockedUntil = attempts.lockedUntil();

        attempts.recordFailure(NOW.plusSeconds(60));

        assertThat(attempts.lockedUntil()).isEqualTo(lockedUntil);
        assertThat(attempts.failedAttempts()).isZero();
    }

    @Test
    void recordsWhenTheLastFailureHappened() {
        attempts.recordFailure(NOW.plusSeconds(30));
        assertThat(attempts.updatedAt()).isEqualTo(NOW.plusSeconds(30));
    }
}
