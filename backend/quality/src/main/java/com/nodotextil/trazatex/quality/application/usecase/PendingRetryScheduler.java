package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Qué hacer cuando Production no responde a un comando (503, otro 5xx, timeout, sin conexión o circuito abierto): <b>no se sabe si lo aplicó</b>,
 * así que nada cambia de estado y se reprograma con el mismo {@code requestId}. Espera creciente (15 s × 2 hasta 5 min), <b>sin tope de
 * intentos</b>, y desde {@code errorAfterAttempts} cada fallo se registra como ERROR. Con el circuito abierto ni siquiera se llamó: se
 * reprograma sin contar un intento.
 */
@Component
class PendingRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingRetryScheduler.class);

    /** Dónde se anota el reintento de un comando pendiente (cada fuente guarda sus columnas). */
    interface Target {

        void recordFailedAttempt(int attempts, Instant nextAttemptAt, String error);

        void reschedule(Instant nextAttemptAt);
    }

    /** Qué está pendiente, para los mensajes: «El {@code what} {@code id} del lote {@code batchId} sigue en {@code state} tras N intentos...». */
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

    /** Un fallo que sí fue una respuesta de Production pero que se reintenta con espera creciente (un 409 al {@code HOLD}: la proyección va atrasada). */
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
