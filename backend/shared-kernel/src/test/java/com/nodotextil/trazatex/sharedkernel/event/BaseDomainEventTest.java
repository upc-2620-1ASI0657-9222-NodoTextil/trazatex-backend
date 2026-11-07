package com.nodotextil.trazatex.sharedkernel.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseDomainEventTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T14:00:00Z"), ZoneOffset.UTC);

    record BatchRegisteredPayload(String batchId, double quantityKg) {
    }

    @Test
    void ofGeneraEventIdVersion1YFechaDelReloj() {
        BaseDomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("batchId", "b-1"), clock);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.type()).isEqualTo("BatchRegistered");
        assertThat(event.aggregateId()).isEqualTo("b-1");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-19T14:00:00Z"));
    }

    @Test
    void cadaEventoTieneUnEventIdDistinto() {
        BaseDomainEvent a = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("k", "v"));
        BaseDomainEvent b = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("k", "v"));

        assertThat(a.eventId()).isNotEqualTo(b.eventId());
    }

    @Test
    void implementaElSobreDomainEvent() {
        DomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("k", "v"));

        assertThat(event.type()).isEqualTo("BatchRegistered");
        assertThat(event.payload()).isEqualTo(Map.of("k", "v"));
    }

    @Test
    void sinCamposObligatoriosNoSeCrea() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new BaseDomainEvent(null, "T", "a", 1, now, "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BaseDomainEvent(id, " ", "a", 1, now, "p")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseDomainEvent(id, "T", null, 1, now, "p")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseDomainEvent(id, "T", "a", 0, now, "p")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseDomainEvent(id, "T", "a", 1, null, "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BaseDomainEvent(id, "T", "a", 1, now, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void seSerializaConLosNombresDelSobreDelCatalogo() throws Exception {
        BaseDomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", new BatchRegisteredPayload("b-1", 12.5), clock);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "eventId", "type", "aggregateId", "version", "occurredAt", "payload");
        assertThat(json.path("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("occurredAt").asString()).isEqualTo("2026-10-19T14:00:00Z");
        assertThat(json.path("payload").path("batchId").asString()).isEqualTo("b-1");
        assertThat(json.path("payload").path("quantityKg").asDouble()).isEqualTo(12.5);
    }

    @Test
    void alDeserializarElPayloadLlegaComoEstructuraGenerica() throws Exception {
        BaseDomainEvent original = BaseDomainEvent.of("BatchSplit", "b-1", Map.of("children", List.of("b-2", "b-3")), clock);

        BaseDomainEvent read = mapper.readValue(mapper.writeValueAsString(original), BaseDomainEvent.class);

        assertThat(read.eventId()).isEqualTo(original.eventId());
        assertThat(read.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(read.payload()).isEqualTo(Map.of("children", List.of("b-2", "b-3")));
    }
}
