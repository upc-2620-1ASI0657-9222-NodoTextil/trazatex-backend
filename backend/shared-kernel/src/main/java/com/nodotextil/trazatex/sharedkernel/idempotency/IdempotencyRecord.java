package com.nodotextil.trazatex.sharedkernel.idempotency;

import java.time.Instant;

/**
 * Fila de {@code idempotency_keys}. {@code responseStatus == 0} es el valor provisional de una clave recién reservada
 * (la columna es NOT NULL); nunca es visible fuera de la transacción que la reservó.
 */
public record IdempotencyRecord(
        String key,
        String userId,
        String requestHash,
        int responseStatus,
        String responseBody,
        Instant createdAt) {
}
