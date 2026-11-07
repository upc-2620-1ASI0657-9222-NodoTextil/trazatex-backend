package com.nodotextil.trazatex.quality.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US26 (gestionar una falla) y US27 (descartar el lote). */
public class FailureSteps extends StepSupport {

    public FailureSteps(World world, QualityApi api, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, jdbc, json);
    }

    // ---- US26 esc. 1: completar la información ----

    @Dado("^que existe una Falla generada por un control$")
    public void aFailureGeneratedByAControlExists() {
        failedBatchWithOpenFailure();
        world.body = new java.util.LinkedHashMap<>(Map.of("rootCause", "Humedad excesiva", "affectedQtyKg", new BigDecimal("12.5"), "observations", "lote húmedo"));
        world.pending = () -> api.put(world.adminA, "/api/quality/failures/" + world.failure, world.body);
    }

    @Cuando("^registra causa, cantidad afectada y observaciones$")
    public void registersCauseQuantityAndObservations() {
        world.submit();
    }

    @Entonces("^el sistema conserva la información$")
    public void theSystemKeepsTheInformation() {
        assertThat(world.last.status()).as("completar la falla: %s", world.last.raw()).isEqualTo(200);
        Map<String, Object> row = failureRow(world.failure);
        assertThat(row).containsEntry("root_cause", "Humedad excesiva").containsEntry("observations", "lote húmedo").containsEntry("status", "OPEN");
        assertThat((BigDecimal) row.get("affected_qty_kg")).isEqualByComparingTo("12.5");
    }

    @Y("^mantiene su relación con el lote, control y pruebas fallidas\\.$")
    public void keepsItsRelationWithTheBatchControlAndFailedTests() {
        Map<String, Object> row = failureRow(world.failure);
        assertThat(row).containsEntry("batch_id", world.batch).containsEntry("control_id", world.control);
        assertThat(count("SELECT count(*) FROM quality_test WHERE control_id = ? AND test_result = 'FAILED'", world.control)).isGreaterThanOrEqualTo(1);
        assertThat(api.get(world.adminA, "/api/quality/failures/" + world.failure).text("controlId")).isEqualTo(world.control.toString());
    }

    // ---- US26 esc. 2 y US27: un lote con falla ----

    @Dado("^que existe un lote Con falla$")
    public void aFailedBatchExists() {
        failedBatchWithOpenFailure();
        if ("us27".equals(world.story)) {
            // Descartar exige causa y cantidad afectada (409 FAILURE_INFO_INCOMPLETE si faltan)
            completeFailureInfo();
            world.pending = () -> api.post(world.adminA, "/api/quality/failures/" + world.failure + "/discard", null);
        } else {
            world.pending = () -> api.post(world.adminA, "/api/quality/failures/" + world.failure + "/reevaluation", null);
        }
    }

    @Cuando("^decide reevaluarlo$")
    public void decidesToReevaluateIt() {
        world.submit();
    }

    @Entonces("^el sistema cambia su calidad a Por reevaluar$")
    public void theSystemChangesTheQualityToPendingReevaluation() {
        assertThat(world.last.status()).as("enviar a reevaluación: %s", world.last.raw()).isEqualTo(200);
        assertThat(projection(world.batch)).containsEntry("quality_status", "PENDING_REEVALUATION");
        assertThat(world.last.text("status")).as("la falla sigue abierta durante la reevaluación").isEqualTo("OPEN");
    }

    @Y("^lo mantiene bloqueado hasta completar la reevaluación\\.$")
    public void keepsItBlockedUntilTheReevaluationIsComplete() {
        assertThat(projection(world.batch)).containsEntry("quality_hold", true);
        assertThat(commandsFor(world.batch)).as("el bloqueo sigue: no se libera la retención").noneMatch(c -> "RELEASE_HOLD".equals(c.path("operation").asString()));
    }

    // ---- US27: descartar ----

    @Cuando("^el administrador decide descartarlo$")
    public void theAdministratorDecidesToDiscardIt() {
        world.submit();
    }

    @Entonces("^el sistema cambia su fase a Descartado$")
    public void theSystemChangesThePhaseToDiscarded() {
        assertThat(world.last.status()).as("descartar el lote: %s", world.last.raw()).isEqualTo(200);
        // La fase la gobierna Production: Quality le manda DISCARD con el id de la falla, y Production confirmó
        assertThat(commandOf(world.batch, "DISCARD").path("requestId").asString()).isEqualTo(world.failure.toString());
    }

    @Y("^registra la Falla como Confirmada\\.$")
    public void recordsTheFailureAsConfirmed() {
        assertThat(failureRow(world.failure)).containsEntry("status", "CONFIRMED");
        assertThat(world.last.field("failure").path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM outbox WHERE event_type = 'FailureConfirmed' AND aggregate_id = ?", world.failure.toString())).isEqualTo(1);
    }

}
