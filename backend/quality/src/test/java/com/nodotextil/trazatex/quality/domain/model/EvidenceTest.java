package com.nodotextil.trazatex.quality.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final EvidenceRetryPolicy POLICY = new EvidenceRetryPolicy(4, Duration.ofSeconds(30), 2.0, Duration.ofMinutes(2));

    private static Evidence pending() {
        return Evidence.pending(UUID.randomUUID(), EvidenceOwnerType.CONTROL, UUID.randomUUID(), UUID.randomUUID(), "a.jpg", ImageFormat.JPEG,
                10, UUID.randomUUID(), NOW, POLICY.initialDelay(), "Cloudinary no respondió");
    }

    @Test
    void theWaitGrowsByTheMultiplierUpToTheCap() {
        assertThat(POLICY.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(POLICY.delayAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(POLICY.delayAfter(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(POLICY.delayAfter(4)).as("tope").isEqualTo(Duration.ofMinutes(2));
        assertThat(POLICY.delayAfter(50)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EvidenceRetryPolicy.DEFAULT.delayAfter(30)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void anInvalidPolicyIsRejected() {
        assertThatThrownBy(() -> new EvidenceRetryPolicy(0, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceRetryPolicy(3, Duration.ofSeconds(1), 0.5, Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceRetryPolicy(3, Duration.ofSeconds(10), 2.0, Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNewPendingEvidenceHasNoUrlNoAttemptsAndAFirstSchedule() {
        Evidence evidence = pending();

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(evidence.url()).isNull();
        assertThat(evidence.uploadedAt()).isNull();
        assertThat(evidence.attempts()).isZero();
        assertThat(evidence.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(evidence.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aTransientFailureCountsAnAttemptAndReschedulesWithGrowingWaits() {
        Evidence evidence = pending();

        evidence.recordTransientFailure("timeout", NOW, POLICY);
        assertThat(evidence.attempts()).isEqualTo(1);
        assertThat(evidence.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        evidence.recordTransientFailure("5xx", NOW, POLICY);
        assertThat(evidence.nextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(evidence.lastError()).isEqualTo("5xx");
    }

    @Test
    void exhaustingTheAttemptsMakesItFailedWithNoMoreSchedule() {
        Evidence evidence = pending();

        for (int i = 0; i < POLICY.maxAttempts(); i++) {
            evidence.recordTransientFailure("5xx", NOW, POLICY);
        }

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.attempts()).isEqualTo(4);
        assertThat(evidence.nextAttemptAt()).isNull();
    }

    @Test
    void aPermanentFailureIsFailedAtOnce() {
        Evidence evidence = pending();

        evidence.recordPermanentFailure("HTTP 400: Invalid image file");

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(evidence.attempts()).isEqualTo(1);
        assertThat(evidence.nextAttemptAt()).isNull();
    }

    @Test
    void deferringWithoutAttemptNeverConsumesAttempts() {
        Evidence evidence = pending();

        for (int i = 0; i < 100; i++) {
            evidence.deferWithoutAttempt("circuito abierto", NOW.plusSeconds(300));
        }

        assertThat(evidence.attempts()).isZero();
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING);
        assertThat(evidence.nextAttemptAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void uploadingSetsTheUrlAndClearsTheScheduleAndTheError() {
        Evidence evidence = pending();
        evidence.recordTransientFailure("timeout", NOW, POLICY);

        evidence.markUploaded("https://res.cloudinary.com/demo/x.jpg", NOW.plusSeconds(40));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.UPLOADED);
        assertThat(evidence.url()).isEqualTo("https://res.cloudinary.com/demo/x.jpg");
        assertThat(evidence.uploadedAt()).isEqualTo(NOW.plusSeconds(40));
        assertThat(evidence.nextAttemptAt()).isNull();
        assertThat(evidence.lastError()).isNull();
    }
}
