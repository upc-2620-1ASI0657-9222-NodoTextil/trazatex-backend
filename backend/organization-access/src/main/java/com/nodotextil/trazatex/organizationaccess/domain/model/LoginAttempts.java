package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class LoginAttempts {

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final int LOCK_MINUTES = 15;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(LOCK_MINUTES);

    private final Email email;
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant updatedAt;

    private LoginAttempts(Email email, int failedAttempts, Instant lockedUntil, Instant updatedAt) {
        this.email = Objects.requireNonNull(email, "email");
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static LoginAttempts none(Email email, Instant now) {
        return new LoginAttempts(email, 0, null, now);
    }

    public static LoginAttempts restore(Email email, int failedAttempts, Instant lockedUntil, Instant updatedAt) {
        return new LoginAttempts(email, failedAttempts, lockedUntil, updatedAt);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailure(Instant now) {
        if (isLocked(now)) {
            return;
        }
        lockedUntil = null;
        failedAttempts++;
        updatedAt = now;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedAttempts = 0;
        }
    }

    public Email email() {
        return email;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
