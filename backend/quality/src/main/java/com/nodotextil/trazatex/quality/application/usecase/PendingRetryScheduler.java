package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
class PendingRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingRetryScheduler.class);

    interface Target {

        void recordFailedAttempt(int attempts, Instant nextAttemptAt, String error);

        void reschedule(Instant nextAttemptAt);
    }

    record Subject(String what, String id, String batchId, String state, String procedure) {
    }

    private final PendingCommandSettings settings;
    private final Clock clock;

    PendingRetryScheduler(PendingCommandSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    void unavailable(Target target, Subject subject, PhaseCommandOutcome.Unavailable unavailable, int attemptsSoFar) {
        Instant now = clock.instant();
        if (unavailable.circuitOpen()) {
            target.reschedule(now.plus(settings.delayAfter(1)));
            log.debug("Circuito de Production abierto: el {} {} espera sin contar un intento", subject.what(), subject.id());
            return;
        }
        int attempts = attemptsSoFar + 1;
        target.recordFailedAttempt(attempts, now.plus(settings.delayAfter(attempts)), unavailable.cause());
        report(subject, attempts, unavailable.cause());
    }

    void retryLater(Target target, Subject subject, int attemptsSoFar, String error) {
        int attempts = attemptsSoFar + 1;
        target.recordFailedAttempt(attempts, clock.instant().plus(settings.delayAfter(attempts)), error);
        report(subject, attempts, error);
    }

    private void report(Subject subject, int attempts, String cause) {
        if (attempts >= settings.errorAfterAttempts()) {
            log.error("El {} {} del lote {} sigue en {} tras {} intentos ({}): Production no responde. Se seguirá intentando; ver el procedimiento «{}» en docs/progress.md",
                    subject.what(), subject.id(), subject.batchId(), subject.state(), attempts, cause, subject.procedure());
        } else {
            log.warn("Production no respondió al comando del {} {} (intento {}): {}. Se reintentará con el mismo requestId", subject.what(), subject.id(), attempts,
                    cause);
        }
    }
}
