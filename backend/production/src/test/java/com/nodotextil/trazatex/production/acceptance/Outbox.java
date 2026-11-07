package com.nodotextil.trazatex.production.acceptance;

import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/** Lee la tabla {@code outbox}: es el contrato con los demás servicios (los eventos del catálogo salen de ahí hacia Kafka). */
public class Outbox {

    /** {@code envelope} es el sobre completo; {@code payload}, el del catálogo. */
    public record Event(UUID id, String type, String aggregateId, String topic, JsonNode envelope) {

        public JsonNode payload() {
            return envelope.path("payload");
        }
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public Outbox(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Event> all() {
        return jdbc.sql("SELECT id, event_type, aggregate_id, topic, payload::text AS payload FROM outbox ORDER BY created_at").query((rs, n) ->
                new Event(UUID.fromString(rs.getString("id")), rs.getString("event_type"), rs.getString("aggregate_id"), rs.getString("topic"),
                        json.readTree(rs.getString("payload")))).list();
    }

    public List<Event> ofType(String type) {
        return all().stream().filter(event -> event.type().equals(type)).toList();
    }

    public List<Event> ofType(String type, String aggregateId) {
        return all().stream().filter(event -> event.type().equals(type) && event.aggregateId().equals(aggregateId)).toList();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single();
    }
}
