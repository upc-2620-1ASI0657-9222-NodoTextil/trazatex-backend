package com.nodotextil.trazatex.quality.acceptance;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nodotextil.trazatex.quality.integration.ProductionStub;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo común de los pasos: preparar un lote (sembrando la proyección que alimentarían los eventos de Production) y leer el estado real. */
abstract class StepSupport {

    protected final World world;
    protected final QualityApi api;
    protected final JdbcClient jdbc;
    protected final ObjectMapper json;

    StepSupport(World world, QualityApi api, JdbcClient jdbc, ObjectMapper json) {
        this.world = world;
        this.api = api;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- preparar ----

    /** Un lote como lo dejarían los eventos de Production: disponible, de la empresa A, con la calidad y la retención dadas. */
    protected UUID seedBatch(String qualityStatus, boolean hold, boolean everControlled) {
        UUID batch = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO batch_projection (batch_id, company_id, phase, quality_status, quality_hold, ever_controlled, batch_version, updated_at)
                VALUES (:id, :company, 'AVAILABLE', :status, :hold, :ever, 1, :at)
                """).param("id", batch).param("company", World.COMPANY_A).param("status", qualityStatus).param("hold", hold).param("ever", everControlled)
                .param("at", OffsetDateTime.now(ZoneOffset.UTC)).update();
        world.batch = batch;
        return batch;
    }

    protected Reply startControl(UUID batch) {
        return api.post(world.adminA, "/api/quality/batches/" + batch + "/controls", null);
    }

    /** Un control en curso sobre el lote, con una prueba por cada resultado dado. */
    protected UUID controlInProgress(UUID batch, String... results) {
        Reply started = startControl(batch);
        assertThat(started.status()).as("iniciar el control: %s", started.raw()).isEqualTo(201);
        world.control = UUID.fromString(started.text("id"));
        for (String result : results) {
            addTest(world.control, result);
        }
        return world.control;
    }

    protected void addTest(UUID control, String result) {
        Reply added = api.post(world.adminA, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Humedad", "expectedValue", "8",
                "obtainedValue", "CONFORMING".equals(result) ? "7" : "15", "unit", "%", "testResult", result, "remarks", "medición de planta"));
        assertThat(added.status()).as("registrar la prueba: %s", added.raw()).isEqualTo(201);
    }

    protected Reply finish(UUID control) {
        return api.post(world.adminA, "/api/quality/controls/" + control + "/finish", null);
    }

    /** Un lote con un control que falló y su falla abierta (la deja como la dejaría el flujo real). */
    protected UUID failedBatchWithOpenFailure() {
        UUID batch = seedBatch("NOT_REVIEWED", false, false);
        UUID control = controlInProgress(batch, "FAILED");
        Reply finished = finish(control);
        assertThat(finished.status()).as("finalizar el control con falla: %s", finished.raw()).isEqualTo(200);
        world.failure = UUID.fromString(finished.field("failure").get("id").asString());
        ProductionStub.reset();
        ProductionStub.answersOk();
        return batch;
    }

    protected void completeFailureInfo() {
        Reply updated = api.put(world.adminA, "/api/quality/failures/" + world.failure, Map.of("rootCause", "Humedad excesiva", "affectedQtyKg", 12.5,
                "observations", "lote húmedo"));
        assertThat(updated.status()).as("completar la falla: %s", updated.raw()).isEqualTo(200);
    }

    protected void sendReevaluation() {
        Reply reply = api.post(world.adminA, "/api/quality/failures/" + world.failure + "/reevaluation", null);
        assertThat(reply.status()).as("enviar a reevaluación: %s", reply.raw()).isEqualTo(200);
    }

    /** Un control finalizado (una prueba conforme y otra con falla) con su falla abierta. */
    protected UUID finishedControl() {
        UUID batch = seedBatch("NOT_REVIEWED", false, false);
        UUID control = controlInProgress(batch, "CONFORMING", "FAILED");
        Reply finished = finish(control);
        assertThat(finished.status()).as("finalizar el control: %s", finished.raw()).isEqualTo(200);
        return control;
    }

    /** Cuántas filas tiene cada tabla de negocio: para comprobar que una operación no guardó nada nuevo. */
    protected Map<String, Long> tableCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (String table : jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'").query(String.class).list()) {
            counts.put(table, jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
        }
        return counts;
    }

    // ---- leer el estado real ----

    protected Map<String, Object> projection(UUID batch) {
        return jdbc.sql("SELECT quality_status, quality_hold, ever_controlled FROM batch_projection WHERE batch_id = :b").param("b", batch).query().singleRow();
    }

    protected Map<String, Object> failureRow(UUID failure) {
        return jdbc.sql("SELECT id, control_id, batch_id, company_id, root_cause, affected_qty_kg, observations, status FROM quality_failure WHERE id = :f")
                .param("f", failure).query().singleRow();
    }

    protected long count(String sql, Object... params) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (int i = 0; i < params.length; i++) {
            spec = spec.param(params[i]);
        }
        return spec.query(Long.class).single();
    }

    /** Los comandos que Production recibió para el lote, con su operación. */
    protected List<JsonNode> commandsFor(UUID batch) {
        return ProductionStub.requestsFor(batch).stream().map(LoggedRequest::getBodyAsString).map(json::readTree).toList();
    }

    protected JsonNode commandOf(UUID batch, String operation) {
        return commandsFor(batch).stream().filter(c -> operation.equals(c.path("operation").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Production no recibió " + operation + " para el lote " + batch + ": " + commandsFor(batch)));
    }
}
