package com.nodotextil.trazatex.sharedkernel.idempotency;

import java.time.Instant;
import java.util.Optional;

/**
 * Almacenamiento de {@code idempotency_keys} (ADR-010). Todas las operaciones se ejecutan dentro de la transacción
 * que abre {@link IdempotencyFilter}; el puerto no abre ni cierra transacciones.
 *
 * <p>Contrato que debe cumplir el adaptador (JPA/JDBC en cada servicio):
 * <ul>
 *   <li>{@link #tryReserve} inserta la fila con {@code response_status = 0} y devuelve {@code false} si la clave ya existe.
 *       Con PostgreSQL: {@code INSERT ... ON CONFLICT DO NOTHING} (una violación de unicidad abortaría la transacción).
 *       Si otra transacción abierta insertó la misma clave, la PK bloquea este insert hasta que aquella confirme
 *       (devuelve {@code false}) o revierta (inserta y devuelve {@code true}).</li>
 *   <li>Se asume aislamiento {@code READ COMMITTED}: tras un {@code false}, {@link #find} ve la fila ya confirmada.</li>
 * </ul>
 */
public interface IdempotencyStorePort {

    /** @return {@code true} si la clave era nueva y quedó reservada por la transacción actual. */
    boolean tryReserve(String key, String userId, String requestHash, Instant createdAt);

    Optional<IdempotencyRecord> find(String key);

    /** Guarda la respuesta de una clave reservada en esta transacción. {@code responseBody} es JSON o {@code null}. */
    void complete(String key, int responseStatus, String responseBody);

    void delete(String key);

    /** Para el job de limpieza de cada servicio (las claves caducan a las 24 h). */
    int deleteCreatedBefore(Instant cutoff);
}
