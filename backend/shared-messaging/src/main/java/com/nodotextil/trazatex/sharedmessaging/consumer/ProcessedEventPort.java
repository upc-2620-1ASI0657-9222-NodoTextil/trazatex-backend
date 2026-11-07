package com.nodotextil.trazatex.sharedmessaging.consumer;

import java.util.UUID;

/**
 * Registro de eventos ya procesados ({@code processed_events}; en Neo4j, nodos {@code ProcessedEvent}). Se usa dentro
 * de la transacción que abre {@link IdempotentEventHandler}.
 *
 * <p>Contrato del adaptador: {@code INSERT ... ON CONFLICT DO NOTHING} sobre la PK {@code event_id} y devolver si se
 * insertó (en Neo4j, {@code MERGE} con restricción de unicidad). Si otra transacción abierta insertó el mismo
 * {@code eventId}, la PK bloquea hasta que confirme (devuelve {@code false}) o revierta (inserta y devuelve {@code true}).
 */
public interface ProcessedEventPort {

    /** @return {@code true} si el evento no se había procesado y queda registrado en la transacción actual. */
    boolean markProcessed(UUID eventId, String eventType);
}
