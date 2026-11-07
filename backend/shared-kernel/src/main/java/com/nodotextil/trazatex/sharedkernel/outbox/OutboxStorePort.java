package com.nodotextil.trazatex.sharedkernel.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Almacenamiento del outbox de un servicio. Se usa siempre dentro de una transacción: {@link #save} dentro de la del
 * caso de uso y {@link #findPending}/{@link #markPublished} dentro de la que abre {@link OutboxRelay}.
 *
 * <p>Contrato del adaptador PostgreSQL:
 * <ul>
 *   <li>{@link #save} inserta la fila con {@code status = 'PENDING'} y el {@code created_at} que trae el mensaje.
 *       No usar {@code DEFAULT now()}: en PostgreSQL es la hora de inicio de la transacción y las filas de una misma
 *       transacción empatarían, perdiendo el orden.</li>
 *   <li>{@link #findPending} devuelve las filas {@code PENDING} ordenadas por {@code created_at}, con
 *       {@code LIMIT :limit FOR UPDATE SKIP LOCKED}: así varias instancias del servicio no publican la misma fila.</li>
 *   <li>{@link #markPublished} pone {@code status = 'PUBLISHED'} y {@code published_at}.</li>
 *   <li>{@link #markFailed} suma 1 a {@code attempts} y guarda {@code last_error}; la fila sigue {@code PENDING}
 *       (no hay estado {@code FAILED}: el relay la reintenta en cada pasada).</li>
 * </ul>
 * En Traceability el adaptador equivalente trabaja con nodos {@code OutboxEvent} dentro de la transacción del grafo.
 */
public interface OutboxStorePort {

    void save(OutboxMessage message);

    /** Mensajes {@code PENDING} más antiguos primero, bloqueados para la transacción actual. */
    List<OutboxMessage> findPending(int limit);

    void markPublished(UUID id, Instant publishedAt);

    /** Registra un intento de publicación fallido. {@code error} llega ya acotado en tamaño. */
    void markFailed(UUID id, String error);
}
