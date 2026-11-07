package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF-11: toda operación sobre lotes, transformaciones y transferencias deja (usuario, empresa, fecha, acción, entidad, cambios). */
class AuditApiIT extends ApiTestBase {

    private record Row(UUID userId, UUID companyId, String action, String entityType, String entityId, String changes, java.time.Instant at) {
    }

    private List<Row> audit() {
        return jdbc.sql("SELECT user_id, company_id, action, entity_type, entity_id, changes::text AS changes, occurred_at FROM audit_log ORDER BY occurred_at, action")
                .query((rs, n) -> new Row(rs.getObject("user_id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("changes"),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant())).list();
    }

    private Row only(String action) {
        List<Row> rows = audit().stream().filter(r -> r.action().equals(action)).toList();
        assertThat(rows).as("fila de %s", action).hasSize(1);
        return rows.get(0);
    }

    @Test
    void everyOperationOfTheFullJourneyIsAuditedWithWhoWhenAndWhat() throws Exception {
        clock.advance(Duration.ofMinutes(1));
        JsonNode machine = registerMachine("MQ-01");
        patchAs(ADMIN_OF_A, "/api/machines/" + machine.path("id").asString() + "/status", obj("status", "OUT_OF_SERVICE")).andExpect(status().isOk());
        patchAs(ADMIN_OF_A, "/api/machines/" + machine.path("id").asString() + "/status", obj("status", "ACTIVE")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        JsonNode batch = registerBatch(100);
        clock.advance(Duration.ofMinutes(1));
        JsonNode split = read(postAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString() + "/split",
                obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60)))).andExpect(status().isCreated()));
        clock.advance(Duration.ofMinutes(1));
        JsonNode transformation = startTransformation("DYEING", idOf(machine), idOf(split.path("children").get(0)));
        clock.advance(Duration.ofMinutes(1));
        JsonNode completed = read(postAs(OPERATOR_OF_A, "/api/transformations/" + transformation.path("id").asString() + "/complete",
                obj("outputs", List.of(obj("materialType", "Tela", "quantityKg", 35, "finalProduct", true)), "wasteKg", 2, "wasteReason", "Orillo"))
                .andExpect(status().isOk()));
        putAs(OPERATOR_OF_A, "/api/batches/" + completed.path("outputs").get(0).path("id").asString() + "/commercial-data",
                obj("buyerOrDistributor", "Tiendas SAC")).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        JsonNode transfer = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B,
                "batchIds", List.of(idOf(split.path("children").get(1))))).andExpect(status().isCreated()));
        clock.advance(Duration.ofMinutes(1));
        postAs(ADMIN_OF_B, "/api/transfers/" + transfer.path("id").asString() + "/accept").andExpect(status().isOk());
        JsonNode second = registerBatch(5);
        JsonNode transfer2 = read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(idOf(second))))
                .andExpect(status().isCreated()));
        postAs(ADMIN_OF_B, "/api/transfers/" + transfer2.path("id").asString() + "/reject", obj("reason", "No lo necesitamos")).andExpect(status().isOk());

        assertThat(audit()).extracting(Row::action).containsExactlyInAnyOrder("MACHINE_REGISTERED", "MACHINE_STATUS_CHANGED",
                "MACHINE_STATUS_CHANGED", "BATCH_REGISTERED", "BATCH_REGISTERED", "BATCH_SPLIT", "TRANSFORMATION_STARTED",
                "TRANSFORMATION_COMPLETED", "BATCH_COMMERCIAL_DATA_UPDATED", "TRANSFER_INITIATED", "TRANSFER_INITIATED", "TRANSFER_ACCEPTED",
                "TRANSFER_REJECTED");

        Row registered = audit().stream().filter(r -> r.action().equals("BATCH_REGISTERED")).findFirst().orElseThrow();
        assertThat(registered.userId()).isEqualTo(OPERATOR_A);
        assertThat(registered.companyId()).isEqualTo(COMPANY_A);
        assertThat(registered.entityType()).isEqualTo("Batch");
        assertThat(registered.entityId()).isEqualTo(batch.path("id").asString());
        assertThat(registered.changes()).contains(batch.path("traceabilityId").asString()).contains("100.000");
        assertThat(registered.at()).isEqualTo(START.plus(Duration.ofMinutes(2)));

        Row splitRow = only("BATCH_SPLIT");
        assertThat(splitRow.entityId()).isEqualTo(batch.path("id").asString());
        assertThat(splitRow.changes()).contains(split.path("children").get(0).path("traceabilityId").asString())
                .contains(split.path("children").get(1).path("traceabilityId").asString());

        Row started = only("TRANSFORMATION_STARTED");
        assertThat(started.entityType()).isEqualTo("Transformation");
        assertThat(started.entityId()).isEqualTo(transformation.path("id").asString());
        assertThat(started.userId()).isEqualTo(OPERATOR_A);

        Row done = only("TRANSFORMATION_COMPLETED");
        assertThat(done.changes()).contains("\"shrinkageKg\"").contains("3.000").contains("\"wasteKg\"").contains("2.000");

        Row accepted = only("TRANSFER_ACCEPTED");
        assertThat(accepted.userId()).isEqualTo(ADMIN_B);
        assertThat(accepted.companyId()).as("la empresa que actúa, no la de origen").isEqualTo(COMPANY_B);
        assertThat(accepted.entityType()).isEqualTo("Transfer");
        Row rejected = only("TRANSFER_REJECTED");
        assertThat(rejected.userId()).isEqualTo(ADMIN_B);
        assertThat(rejected.changes()).contains("No lo necesitamos");

        Row statusChange = audit().stream().filter(r -> r.action().equals("MACHINE_STATUS_CHANGED")).findFirst().orElseThrow();
        assertThat(statusChange.userId()).isEqualTo(ADMIN_A);
        assertThat(statusChange.entityType()).isEqualTo("Machine");
        assertThat(jdbc.sql("SELECT DISTINCT event_type FROM outbox").query(String.class).list())
                .as("la auditoría y los eventos son cosas distintas: ambas quedan en la misma transacción")
                .contains("BatchRegistered", "BatchSplit", "BatchPhaseChanged", "TransformationCompleted", "TransferInitiated",
                        "TransferAccepted", "TransferRejected");
    }

    @Test
    void anOperationThatFailsLeavesNoAuditRow() throws Exception {
        JsonNode batch = registerBatch(100);
        long before = count("SELECT count(*) FROM audit_log");

        postAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString() + "/split", obj("children", List.of(obj("quantityKg", 1), obj("quantityKg", 2))))
                .andExpect(status().isBadRequest());
        postAs(OPERATOR_OF_B, "/api/batches/" + batch.path("id").asString() + "/split", obj("children", List.of(obj("quantityKg", 40), obj("quantityKg", 60))))
                .andExpect(status().isNotFound());
        postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_A, "batchIds", List.of(idOf(batch)))).andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(before);
    }

    @Test
    void readingLeavesNoAuditRows() throws Exception {
        JsonNode batch = registerBatch(100);
        long before = count("SELECT count(*) FROM audit_log");

        getAs(OPERATOR_OF_A, "/api/batches").andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches/" + batch.path("id").asString()).andExpect(status().isOk());
        getAs(OPERATOR_OF_A, "/api/batches/qr/" + batch.path("qrCode").asString()).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(before);
    }
}
