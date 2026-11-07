package com.nodotextil.trazatex.notification.application.port.out;

import com.nodotextil.trazatex.notification.application.dto.DueEmail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cola de correos diferidos (ADR-007). Un correo solo conserva su cuerpo mientras está {@code PENDING}: en cualquier estado final
 * ({@code SENT}, {@code FAILED}, {@code EXPIRED}, {@code UNDELIVERABLE}) el cuerpo se vacía, porque una invitación lleva un token de un solo uso.
 */
public interface DeferredEmailStorePort {
    /** Pasa a {@code EXPIRED} los pendientes cuya invitación ya venció. */
    int expireDue();

    /** Toma hasta {@code limit} correos pendientes y vencidos y los arrienda {@code lease}: otro job no los toma, aunque este se caiga. No consume intentos. */
    List<DueEmail> claimDue(int limit, Duration lease);

    void markSent(UUID id);
    void markUndeliverable(UUID id, String reason);
    /** Un intento fallido que se repetirá: suma un intento y fija el siguiente. */
    void markRetry(UUID id, Instant nextAttempt, String reason);
    /** Final: suma un intento. */
    void markFailed(UUID id, String reason);
    /** Devuelve el correo a la cola sin consumir un intento. */
    void release(UUID id, Instant nextAttempt);
}
