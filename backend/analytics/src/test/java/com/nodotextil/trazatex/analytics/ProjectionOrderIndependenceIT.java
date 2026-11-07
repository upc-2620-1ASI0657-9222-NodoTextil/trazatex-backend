package com.nodotextil.trazatex.analytics;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I9: el resultado no depende del orden en que lleguen los eventos entre tópicos ni del reloj. Se entrega el mismo conjunto de eventos en 30
 * órdenes distintos (con una entrega repetida de cada uno) y el resumen tiene que ser siempre el mismo.
 */
class ProjectionOrderIndependenceIT extends AnalyticsIntegrationTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    static final Instant T = Instant.parse("2026-10-10T10:00:00Z");

    private List<String> events() {
        List<String> events = new ArrayList<>();
        UUID lot1 = UUID.randomUUID(), lot2 = UUID.randomUUID(), transfer = UUID.randomUUID();
        events.add(envelope("BatchRegistered", lot1, Map.of("batchId", lot1, "companyId", a), T));
        events.add(envelope("BatchRegistered", lot2, Map.of("batchId", lot2, "companyId", a), T));
        // lot1 pasa a b (v4) y luego a c (v7); las horas están al revés a propósito: v7 con la hora MÁS temprana
        events.add(phase(lot1, b, 4, T.plusSeconds(500)));
        events.add(phase(lot1, c, 7, T.plusSeconds(400)));
        events.add(phase(lot1, a, 2, T.plusSeconds(900)));                         // v2 con el reloj más adelantado
        events.add(envelope("QualityStatusChanged", lot1, Map.of("batchId", lot1, "companyId", a, "newStatus", "CONFORMING"), T.plusSeconds(60)));
        events.add(envelope("QualityStatusChanged", lot2, Map.of("batchId", lot2, "companyId", a, "newStatus", "FAILED"), T.plusSeconds(61)));
        events.add(envelope("TransferInitiated", transfer, Map.of("fromCompanyId", a, "toCompanyId", b), T));
        events.add(envelope("TransferAccepted", transfer, Map.of("fromCompanyId", a, "toCompanyId", b, "acceptedAt", T.plusSeconds(7200)), T.plusSeconds(7200)));
        UUID failure = UUID.randomUUID();
        events.add(envelope("QualityFailureUpdated", failure, failureOf(failure, null, "OPEN"), T.plusSeconds(10)));
        events.add(envelope("QualityFailureUpdated", failure, failureOf(failure, "Fibra corta", "OPEN"), T.plusSeconds(20)));
        UUID tr = UUID.randomUUID();
        events.add(envelope("TransformationCompleted", tr, Map.of("companyId", a, "outputs", List.of(Map.of("batchId", UUID.randomUUID())),
                "shrinkageKg", 1.5, "wasteKg", 2, "startedAt", T.minusSeconds(3600), "completedAt", T), T));
        return events;
    }
    private Map<String, Object> failureOf(UUID id, String cause, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failureId", id); payload.put("batchId", UUID.randomUUID()); payload.put("companyId", a); payload.put("cause", cause); payload.put("status", status);
        return payload;
    }
    private String phase(UUID lot, UUID owner, long version, Instant at) {
        return envelope("BatchPhaseChanged", lot, Map.of("batchId", lot, "companyId", owner, "previousPhase", "AVAILABLE", "newPhase", "AVAILABLE",
                "qualityHold", false, "reason", "x", "batchVersion", version), at);
    }

    private String snapshot() {
        JsonNode result = summary(a, "2026-10-01", "2026-10-31");
        return ((tools.jackson.databind.node.ObjectNode) result).deepCopy().without("generatedAt").toString()
                + "|" + summary(b, "2026-10-01", "2026-10-31").path("quality") + "|" + summary(c, "2026-10-01", "2026-10-31").path("quality");
    }

    @Test
    void theSameEventsInAnyOrderGiveTheSameResult() {
        List<String> events = events();
        String expected = null;
        Random random = new Random(20261104);
        for (int round = 0; round < 30; round++) {
            jdbc.sql("TRUNCATE analytics_event,batch_birth,batch_owner,batch_quality").update();
            List<String> shuffled = new ArrayList<>(events);
            shuffled.addAll(events.subList(0, 4));                                      // entregas repetidas
            Collections.shuffle(shuffled, random);
            shuffled.forEach(projection::consume);
            String current = snapshot();
            if (expected == null) expected = current; else assertThat(current).as("orden %s", round).isEqualTo(expected);
        }
        // y el dueño del lote 1 es «c» (v7), no «a» (v2, el reloj más adelantado) ni «b» (v4)
        Map<String, Long> ofA = new LinkedHashMap<>(), ofC = new LinkedHashMap<>();
        summary(a, "2026-10-01", "2026-10-31").path("quality").forEach(row -> ofA.put(row.path("status").asString(), row.path("count").asLong()));
        summary(c, "2026-10-01", "2026-10-31").path("quality").forEach(row -> ofC.put(row.path("status").asString(), row.path("count").asLong()));
        assertThat(ofA).containsExactly(Map.entry("FAILED", 1L), Map.entry("NOT_REVIEWED", 1L));         // lot2 y la salida de la transformación; lot1 ya no es de «a»
        assertThat(ofC).containsExactly(Map.entry("CONFORMING", 1L));                                     // lot1: v7
        assertThat(summary(b, "2026-10-01", "2026-10-31").path("quality")).isEmpty();
    }
}
