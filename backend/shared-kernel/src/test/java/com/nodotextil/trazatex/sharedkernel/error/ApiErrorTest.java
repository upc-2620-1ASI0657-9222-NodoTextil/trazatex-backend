package com.nodotextil.trazatex.sharedkernel.error;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiErrorTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void seSerializaConCodeMessageDetailsYTimestampIso() throws Exception {
        ApiError error = new ApiError("BATCH_NOT_FOUND", "El lote no existe", List.of("batchId=b-1"),
                Instant.parse("2026-10-19T14:00:00Z"));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(error));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(json.path("code").asString()).isEqualTo("BATCH_NOT_FOUND");
        assertThat(json.path("message").asString()).isEqualTo("El lote no existe");
        assertThat(json.path("details").get(0).asString()).isEqualTo("batchId=b-1");
        assertThat(json.path("timestamp").asString()).isEqualTo("2026-10-19T14:00:00Z");
    }

    @Test
    void ofSinDetalleDejaDetailsVacioYPoneTimestamp() {
        ApiError error = ApiError.of("X", "mensaje");

        assertThat(error.details()).isEmpty();
        assertThat(error.timestamp()).isNotNull();
    }

    @Test
    void detailsEsInmutableYNuloSeVuelveVacio() {
        List<String> source = new ArrayList<>(List.of("a"));
        ApiError error = ApiError.of("X", "m", source);
        source.add("b");

        assertThat(error.details()).containsExactly("a");
        assertThatThrownBy(() -> error.details().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ApiError("X", "m", null, null).details()).isEmpty();
    }

    @Test
    void codeYMessageSonObligatorios() {
        assertThatThrownBy(() -> ApiError.of(null, "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApiError.of("X", null)).isInstanceOf(NullPointerException.class);
    }
}
