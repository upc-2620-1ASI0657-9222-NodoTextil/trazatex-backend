package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * {@code (:ProcessedEvent {eventId})}. Se usa dentro de la transacción que abre {@code IdempotentEventHandler}, que es la
 * <b>misma</b> transacción de Neo4j que cambia el grafo y escribe el {@code :OutboxEvent} (ADR-004): el
 * {@code Neo4jTransactionManager} es el único gestor del servicio y el {@code Neo4jClient} participa de ella.
 *
 * <p>{@code MERGE} sobre la restricción de unicidad: si otra transacción abierta creó el mismo {@code eventId}, esta espera
 * a que termine (y entonces solo encuentra el nodo: duplicado) o a que se revierta (y entonces lo crea).
 */
@Component
class Neo4jProcessedEventAdapter implements ProcessedEventPort {

    private final Neo4jClient neo4j;
    private final Clock clock;

    Neo4jProcessedEventAdapter(Neo4jClient neo4j, Clock clock) {
        this.neo4j = neo4j;
        this.clock = clock;
    }

    @Override
    public boolean markProcessed(UUID eventId, String eventType) {
        return neo4j.query("""
                        MERGE (p:ProcessedEvent {eventId: $eventId})
                        ON CREATE SET p.eventType = $eventType, p.processedAt = datetime($processedAt)
                        """)
                .bind(eventId.toString()).to("eventId")
                .bind(eventType).to("eventType")
                .bind(clock.instant().toString()).to("processedAt")
                .run().counters().nodesCreated() == 1;
    }
}
