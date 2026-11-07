package com.nodotextil.trazatex.analytics;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de EP05 que los escenarios de user-stories.md no dicen y que las decisiones del 2026-11-04 fijan: US37 es el estado AL CIERRE del
 * periodo y la dueña del lote la decide {@code batchVersion}; US38 cuenta fallas registradas; US39 cuenta solo transferencias aceptadas y solo para
 * la empresa de origen; los límites del periodo son los de America/Lima; nada de otra empresa entra.
 */
class IndicatorRulesIT extends AnalyticsIntegrationTest {
    final UUID company = UUID.randomUUID(), other = UUID.randomUUID(), destination = UUID.randomUUID();
    static final Instant OCT_10 = Instant.parse("2026-10-10T10:00:00Z");

    Instant plus(long seconds) { return OCT_10.plusSeconds(seconds); }

    void register(UUID batch, UUID owner, Instant at) {
        deliver("BatchRegistered", batch, Map.of("batchId", batch, "companyId", owner, "createdAt", at), at);
    }
    void status(UUID batch, UUID owner, String status, Instant at) {
        deliver("QualityStatusChanged", batch, Map.of("batchId", batch, "companyId", owner, "newStatus", status), at);
    }
    String phaseChanged(UUID batch, UUID owner, long version, Instant at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batch); payload.put("companyId", owner); payload.put("previousPhase", "AVAILABLE"); payload.put("newPhase", "AVAILABLE");
        payload.put("qualityHold", false); payload.put("reason", "prueba"); payload.put("batchVersion", version);
        return deliver("BatchPhaseChanged", batch, payload, at);
    }
    void failure(UUID failure, UUID owner, String cause, String state, Instant at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failureId", failure); payload.put("batchId", UUID.randomUUID()); payload.put("companyId", owner);
        payload.put("cause", cause); payload.put("status", state);
        deliver("QualityFailureUpdated", failure, payload, at);
    }
    void transfer(UUID id, UUID from, UUID to, Instant started, Instant finished, boolean accepted) {
        deliver("TransferInitiated", id, Map.of("transferId", id, "batchIds", List.of(), "fromCompanyId", from, "toCompanyId", to), started);
        if (accepted) deliver("TransferAccepted", id, Map.of("transferId", id, "batchIds", List.of(), "fromCompanyId", from, "toCompanyId", to, "acceptedAt", finished), finished);
        else deliver("TransferRejected", id, Map.of("transferId", id, "batchIds", List.of(), "fromCompanyId", from, "toCompanyId", to), finished);
    }
    void transformation(UUID owner, double shrinkage, double waste, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        deliver("TransformationCompleted", id, Map.of("transformationId", id, "companyId", owner, "outputs", List.of(Map.of("batchId", UUID.randomUUID())),
                "shrinkageKg", shrinkage, "wasteKg", waste, "startedAt", start, "completedAt", end), end);
    }
    static Map<String, Long> counts(JsonNode rows, String key) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (JsonNode row : rows) result.put(row.path(key).asString(), row.path("count").asLong());
        return result;
    }

    // ---- US37 ----

    @Test
    void qualityIsTheStateAtThePeriodCloseNotTheCurrentOne() {
        UUID batch = UUID.randomUUID();
        register(batch, company, OCT_10);
        status(batch, company, "CONFORMING", plus(86_400));
        status(batch, company, "FAILED", Instant.parse("2026-11-05T10:00:00Z"));

        assertThat(counts(summary(company, "2026-10-01", "2026-10-31").path("quality"), "status")).containsExactly(Map.entry("CONFORMING", 1L));
        // el mismo lote, mirado al cierre de noviembre: ya estaba FAILED (el lote se registró en octubre, dentro del periodo 01-oct a 30-nov)
        assertThat(counts(summary(company, "2026-10-01", "2026-11-30").path("quality"), "status")).containsExactly(Map.entry("FAILED", 1L));
    }

    @Test
    void theOwnerAtCloseIsTheOneWithTheGreatestBatchVersionWhateverTheClockAndTheArrivalOrder() {
        UUID batch = UUID.randomUUID();
        register(batch, company, OCT_10);
        // v5: la transferencia pasa el lote a «destination». v3 es anterior en versión pero su reloj va ADELANTE y llega DESPUÉS.
        phaseChanged(batch, destination, 5, plus(100));
        phaseChanged(batch, company, 3, plus(101));

        assertThat(summary(destination, "2026-10-01", "2026-10-31").path("quality")).hasSize(1);
        assertThat(summary(company, "2026-10-01", "2026-10-31").path("quality")).isEmpty();
    }

    @Test
    void aTransferMovesTheLotToItsNewOwnerOnlyAfterTheClose() {
        UUID batch = UUID.randomUUID();
        register(batch, company, OCT_10);
        phaseChanged(batch, destination, 2, Instant.parse("2026-11-03T10:00:00Z"));

        assertThat(summary(company, "2026-10-01", "2026-10-31").path("quality")).hasSize(1);       // al cierre de octubre aún era suyo
        assertThat(summary(destination, "2026-10-01", "2026-10-31").path("quality")).isEmpty();
        assertThat(summary(destination, "2026-10-01", "2026-11-30").path("quality")).hasSize(1);   // al cierre de noviembre ya es del destino
    }

    @Test
    void splitChildrenAndTransformationOutputsAreBornWithTheirOwnerAndState() {
        UUID parent = UUID.randomUUID(), child = UUID.randomUUID();
        deliver("BatchSplit", parent, Map.of("parentBatchId", parent, "companyId", company, "children", List.of(Map.of("batchId", child, "qualityStatus", "CONFORMING"))), OCT_10);
        transformation(company, 0, 0, plus(-3600), plus(0));

        assertThat(counts(summary(company, "2026-10-01", "2026-10-31").path("quality"), "status"))
                .containsEntry("CONFORMING", 1L).containsEntry("NOT_REVIEWED", 1L).hasSize(2);
    }

    @Test
    void percentagesAddUpToOneHundredAndAnEmptyPeriodIsZerosAndEmptyLists() {
        for (String s : List.of("CONFORMING", "FAILED", "FAILED")) {
            UUID batch = UUID.randomUUID();
            register(batch, company, OCT_10);
            status(batch, company, s, plus(60));
        }
        double total = 0;
        for (JsonNode row : summary(company, "2026-10-01", "2026-10-31").path("quality")) total += row.path("percentage").asDouble();
        assertThat(total).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.011));

        JsonNode empty = summary(company, "2000-01-01", "2000-01-02");
        assertThat(empty.path("quality")).isEmpty();
        assertThat(empty.path("failureCauses")).isEmpty();
        assertThat(empty.path("durations")).isEmpty();
        assertThat(empty.path("materials").path("shrinkageKg").asDouble()).isZero();
        assertThat(empty.path("materials").path("wasteKg").asDouble()).isZero();
        assertThat(empty.path("materials").path("completedTransformations").asInt()).isZero();
        assertThat(empty.path("timeZone").asString()).isEqualTo("America/Lima");
    }

    // ---- US38 ----

    @Test
    void failuresAreCountedOnceWhateverTheirStateWithTheirLatestCause() {
        UUID open = UUID.randomUUID(), resolved = UUID.randomUUID(), confirmed = UUID.randomUUID(), noCause = UUID.randomUUID(), september = UUID.randomUUID();
        failure(open, company, null, "OPEN", plus(0));
        failure(open, company, "Fibra corta", "OPEN", plus(60));               // la causa llega después: cuenta la última
        failure(resolved, company, "Fibra corta", "OPEN", plus(120));
        failure(resolved, company, "Fibra corta", "RESOLVED", plus(180));      // abierta y luego resuelta: sigue siendo UNA falla registrada
        failure(confirmed, company, "Manchas", "CONFIRMED", plus(240));
        failure(noCause, company, "  ", "OPEN", plus(300));
        failure(september, company, "Humedad", "OPEN", Instant.parse("2026-09-20T10:00:00Z"));
        failure(september, company, "Humedad", "RESOLVED", plus(360));         // nació en septiembre: no es de octubre aunque cambie en octubre
        failure(UUID.randomUUID(), other, "Ajena", "OPEN", plus(0));

        assertThat(counts(summary(company, "2026-10-01", "2026-10-31").path("failureCauses"), "cause"))
                .containsExactly(Map.entry("Fibra corta", 2L), Map.entry("Causa por registrar", 1L), Map.entry("Manchas", 1L));
    }

    // ---- US39 ----

    @Test
    void transfersCountOnlyWhenAcceptedAndOnlyForTheOriginCompany() {
        transfer(UUID.randomUUID(), company, destination, plus(0), plus(10_800), true);          // 3 h, aceptada: cuenta al origen
        transfer(UUID.randomUUID(), company, destination, plus(0), plus(36_000), false);         // 10 h, rechazada: no cuenta
        transfer(UUID.randomUUID(), other, company, plus(0), plus(7_200), true);                 // 2 h, la recibe «company»: no cuenta para ella

        JsonNode origin = summary(company, "2026-10-01", "2026-10-31").path("durations");
        assertThat(origin).hasSize(1);
        assertThat(origin.get(0).path("operation").asString()).isEqualTo("Transferencias");
        assertThat(origin.get(0).path("count").asInt()).isEqualTo(1);
        assertThat(origin.get(0).path("averageHours").asDouble()).isEqualTo(3.0);

        assertThat(summary(destination, "2026-10-01", "2026-10-31").path("durations")).isEmpty();      // recibió dos y no cuenta ninguna
        JsonNode sender = summary(other, "2026-10-01", "2026-10-31").path("durations");
        assertThat(sender.get(0).path("count").asInt()).isEqualTo(1);
        assertThat(sender.get(0).path("averageHours").asDouble()).isEqualTo(2.0);
    }

    @Test
    void controlsAndTransformationsAverageTheirDurationAndTheTransferIsDatedByItsAcceptance() {
        transformation(company, 1, 1, plus(-7200), plus(0));
        transformation(company, 1, 1, plus(0), plus(14_400));                    // 2 h y 4 h: promedio 3 h
        UUID control = UUID.randomUUID();
        deliver("QualityControlCompleted", control, Map.of("controlId", control, "companyId", company, "startedAt", plus(0), "finishedAt", plus(3_600)), plus(3_600));
        // iniciada en septiembre y aceptada en octubre: entra en octubre porque cuenta la fecha de aceptación
        transfer(UUID.randomUUID(), company, destination, Instant.parse("2026-09-30T20:00:00Z"), plus(0), true);

        Map<String, Double> averages = new LinkedHashMap<>();
        for (JsonNode row : summary(company, "2026-10-01", "2026-10-31").path("durations")) averages.put(row.path("operation").asString(), row.path("averageHours").asDouble());
        assertThat(averages).containsEntry("Transformaciones", 3.0).containsEntry("Controles de calidad", 1.0).containsKey("Transferencias");
    }

    // ---- US40 y el periodo ----

    @Test
    void lossesAddUpAndOnlyTheCompanyOfTheTransformationCounts() {
        transformation(company, 1, 1, plus(-7200), plus(0));
        transformation(company, 0.5, 2, plus(0), plus(3_600));
        transformation(other, 9, 9, plus(0), plus(3_600));

        JsonNode materials = summary(company, "2026-10-01", "2026-10-31").path("materials");
        assertThat(materials.path("shrinkageKg").asDouble()).isEqualTo(1.5);
        assertThat(materials.path("wasteKg").asDouble()).isEqualTo(3.0);
        assertThat(materials.path("completedTransformations").asInt()).isEqualTo(2);
    }

    @Test
    void thePeriodIsInclusiveAndItsLimitsAreMidnightInLima() {
        // 31-oct 23:30 en Lima = 01-nov 04:30Z: es de octubre. 01-nov 00:00 en Lima = 05:00Z: ya es de noviembre.
        transformation(company, 1, 1, Instant.parse("2026-11-01T02:30:00Z"), Instant.parse("2026-11-01T04:30:00Z"));
        transformation(company, 5, 5, Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-11-01T05:00:00Z"));
        // 01-oct 00:00 en Lima = 05:00Z entra; 30-sep 23:59 en Lima = 04:59Z no
        transformation(company, 7, 7, Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-10-01T05:00:00Z"));
        transformation(company, 3, 3, Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-10-01T04:59:00Z"));

        assertThat(summary(company, "2026-10-01", "2026-10-31").path("materials").path("shrinkageKg").asDouble()).isEqualTo(8.0);
    }

    // ---- idempotencia ----

    @Test
    void aRepeatedEventChangesNothing() {
        UUID batch = UUID.randomUUID();
        String json = deliver("BatchRegistered", batch, Map.of("batchId", batch, "companyId", company), OCT_10);
        String phase = phaseChanged(batch, destination, 4, plus(5));
        long before = jdbc.sql("SELECT (SELECT count(*) FROM analytics_event)+(SELECT count(*) FROM batch_birth)+(SELECT count(*) FROM batch_owner)+(SELECT count(*) FROM batch_quality)")
                .query(Long.class).single();
        projection.consume(json);
        projection.consume(phase);
        assertThat(jdbc.sql("SELECT (SELECT count(*) FROM analytics_event)+(SELECT count(*) FROM batch_birth)+(SELECT count(*) FROM batch_owner)+(SELECT count(*) FROM batch_quality)")
                .query(Long.class).single()).isEqualTo(before);
    }
}
