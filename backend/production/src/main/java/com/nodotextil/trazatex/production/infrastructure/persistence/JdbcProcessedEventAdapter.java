package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** {@code processed_events}: {@code INSERT ... ON CONFLICT DO NOTHING}; la PK bloquea a un duplicado concurrente hasta que la otra transacción termine. */
@Component
class JdbcProcessedEventAdapter implements ProcessedEventPort {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcProcessedEventAdapter(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean markProcessed(UUID eventId, String eventType) {
        return jdbc.sql("""
                        INSERT INTO processed_events (event_id, event_type, processed_at)
                        VALUES (:eventId, :eventType, :processedAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("processedAt", Timestamps.toDb(clock.instant()))
                .update() == 1;
    }
}
