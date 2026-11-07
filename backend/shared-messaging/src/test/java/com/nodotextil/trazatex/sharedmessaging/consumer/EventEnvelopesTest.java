package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopesTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    enum Color { ROJO, AZUL }

    record Payload(UUID id, Color color) {
    }

    private BaseDomainEvent event(Object payload) {
        return BaseDomainEvent.of("X", "agg-1", payload);
    }

    @Test
    void unSobreValidoSeLeeConSuPayloadGenerico() {
        UUID id = UUID.randomUUID();
        String json = mapper.writeValueAsString(event(new Payload(id, Color.AZUL)));

        BaseDomainEvent parsed = EventEnvelopes.parse(mapper, json);

        assertThat(parsed.type()).isEqualTo("X");
        assertThat(EventEnvelopes.payload(mapper, parsed, Payload.class)).isEqualTo(new Payload(id, Color.AZUL));
    }

    @Test
    void unTextoQueNoEsUnSobreEsMalformado() {
        assertThatThrownBy(() -> EventEnvelopes.parse(mapper, "esto no es un sobre")).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> EventEnvelopes.parse(mapper, "{\"type\":\"X\"}")).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void unPayloadConUnValorImposibleEsMalformadoYNoSeReintenta() {
        BaseDomainEvent parsed = event(Map.of("id", UUID.randomUUID().toString(), "color", "VERDE"));

        assertThatThrownBy(() -> EventEnvelopes.payload(mapper, parsed, Payload.class))
                .isInstanceOf(MalformedEventException.class).isInstanceOf(NonRetryableEventException.class);
    }
}
