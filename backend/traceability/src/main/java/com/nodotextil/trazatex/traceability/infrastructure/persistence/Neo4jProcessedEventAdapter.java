package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

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
