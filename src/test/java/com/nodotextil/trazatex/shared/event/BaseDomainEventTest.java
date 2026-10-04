package com.nodotextil.trazatex.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseDomainEventTest {

    static class SampleEvent extends BaseDomainEvent {
        SampleEvent(UUID aggregateId) { super(aggregateId, 1); }
    }

    @Test
    void generatesUniqueEventIdAndCapturesOccurredAt() {
        UUID aggregateId = UUID.randomUUID();
        SampleEvent event = new SampleEvent(aggregateId);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.type()).isEqualTo("SampleEvent");
    }

    @Test
    void eachInstanceHasADifferentEventId() {
        UUID aggregateId = UUID.randomUUID();
        SampleEvent first = new SampleEvent(aggregateId);
        SampleEvent second = new SampleEvent(aggregateId);

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
