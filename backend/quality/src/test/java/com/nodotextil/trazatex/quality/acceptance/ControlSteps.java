package com.nodotextil.trazatex.quality.acceptance;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US23 (iniciar), US24 (pruebas), US25 (finalizar), US28 (reevaluación) y US34 (falla derivada): el control y su resultado. */
public class ControlSteps extends StepSupport {

    public ControlSteps(World world, QualityApi api, JdbcClient jdbc, ObjectMapper json) {
        super(world, api, jdbc, json);
    }

    private Instant instantOf(String column, UUID control) {
        return jdbc.sql("SELECT " + column + " FROM quality_control WHERE id = :id").param("id", control)
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class)).single().toInstant();
    }

    private void assertWithinTheRequest(Instant moment) {
        assertThat(moment).isBetween(world.sentAt.minus(Duration.ofSeconds(2)), world.answeredAt.plus(Duration.ofSeconds(2)));
    }

    // ---- US23: iniciar un control ----

    @Dado("^que el lote tiene calidad No revisado$")
    public void theBatchIsNotReviewed() {
        UUID batch = seedBatch("NOT_REVIEWED", false, false);
        world.pending = () -> startControl(batch);
    }

    @Dado("^que el lote tiene calidad Por reevaluar$")
    public void theBatchIsPendingReevaluation() {
        UUID batch = failedBatchWithOpenFailure();
        sendReevaluation();
        world.pending = () -> startControl(batch);
    }

    @Dado("^que el lote posee un control activo$")
    public void theBatchHasAnActiveControl() {
        UUID batch = seedBatch("NOT_REVIEWED", false, false);
        controlInProgress(batch);
    }

    @Cuando("^el administrador inicia un control$")
    public void theAdministratorStartsAControl() {
        world.submit();
    }

    @Cuando("^intenta iniciar otro$")
    public void triesToStartAnother() {
        world.send(() -> startControl(world.batch));
    }

    @Entonces("^el sistema crea una Evaluación$")
    public void theSystemCreatesAnEvaluation() {
        assertThat(world.last.status()).as("iniciar el control: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("controlType")).isEqualTo("EVALUATION");
        assertThat(world.last.text("status")).isEqualTo("IN_PROGRESS");
        world.control = UUID.fromString(world.last.text("id"));
        assertThat(count("SELECT count(*) FROM quality_control WHERE batch_id = ? AND control_type = 'EVALUATION'", world.batch)).isEqualTo(1);
    }

    @Entonces("^el sistema crea una Reevaluación\\.$")
    public void theSystemCreatesAReevaluation() {
        assertThat(world.last.status()).as("iniciar la reevaluación: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("controlType")).isEqualTo("REEVALUATION");
        assertThat(world.last.text("status")).isEqualTo("IN_PROGRESS");
    }

    @Y("^registra fecha y hora de inicio$")
    public void recordsTheStartDateAndTime() {
        assertWithinTheRequest(instantOf("start_date", world.control));
        assertThat(world.last.text("startDate")).isNotBlank();
    }

    @Y("^cambia la fase a En control de calidad\\.$")
    public void changesThePhaseToInQualityControl() {
        // La fase la gobierna Production: Quality le manda START_EVALUATION con el id del control, y Production confirmó
        assertThat(commandOf(world.batch, "START_EVALUATION").path("requestId").asString()).isEqualTo(world.control.toString());
        assertThat(count("SELECT count(*) FROM quality_control WHERE id = ? AND status = 'IN_PROGRESS'", world.control)).isEqualTo(1);
    }

    @Entonces("^el sistema no permite iniciarlo\\.$")
    public void theSystemDoesNotAllowStartingIt() {
        assertThat(world.last.status()).as("iniciar otro control: %s", world.last.raw()).isEqualTo(409);
        assertThat(world.last.code()).isEqualTo("CONTROL_ALREADY_ACTIVE");
        assertThat(count("SELECT count(*) FROM quality_control WHERE batch_id = ?", world.batch)).isEqualTo(1);
        assertThat(commandsFor(world.batch)).as("no se manda otro comando a Production").hasSize(1);
    }

    // ---- US24: registrar pruebas ----

    @Dado("^que existe un control activo$")
    public void anActiveControlExists() {
        controlInProgress(seedBatch("NOT_REVIEWED", false, false));
    }

    @Y("^el administrador ingresa criterio, valor esperado, valor obtenido, unidad, resultado y observaciones$")
    public void theAdministratorEntersTheTest() {
        world.body = new java.util.LinkedHashMap<>(Map.of("criterionName", "Resistencia a la tracción", "expectedValue", "50", "obtainedValue", "52,3",
                "unit", "N", "testResult", "CONFORMING", "remarks", "dentro del rango"));
        world.pending = () -> api.post(world.adminA, "/api/quality/controls/" + world.control + "/tests", world.body);
        assertThat(world.body).as("lo que el administrador ingresó").containsKeys("criterionName", "expectedValue", "obtainedValue", "unit", "testResult", "remarks");
    }

    @Cuando("^confirma el registro$")
    public void confirmsTheRegistration() {
        world.submit();
    }

    @Entonces("^el sistema conserva la prueba$")
    public void theSystemKeepsTheTest() {
        assertThat(world.last.status()).as("registrar la prueba: %s", world.last.raw()).isEqualTo(201);
        Map<String, Object> row = jdbc.sql("SELECT criterion_name, expected_value, obtained_value, unit, test_result, remarks FROM quality_test WHERE id = :id")
                .param("id", UUID.fromString(world.last.text("id"))).query().singleRow();
        assertThat(row).containsEntry("criterion_name", "Resistencia a la tracción").containsEntry("expected_value", "50").containsEntry("obtained_value", "52,3")
                .containsEntry("unit", "N").containsEntry("test_result", "CONFORMING").containsEntry("remarks", "dentro del rango");
    }

    @Y("^la asocia con el control correspondiente\\.$")
    public void associatesItWithTheControl() {
        UUID test = UUID.fromString(world.last.text("id"));
        assertThat(jdbc.sql("SELECT control_id FROM quality_test WHERE id = :id").param("id", test).query(UUID.class).single()).isEqualTo(world.control);
        Reply control = api.get(world.adminA, "/api/quality/controls/" + world.control);
        assertThat(control.field("tests")).hasSize(1);
        assertThat(control.field("tests").get(0).path("id").asString()).isEqualTo(test.toString());
    }

    // ---- US25: finalizar ----

    @Dado("^que todas las pruebas son Conformes$")
    public void allTheTestsAreConforming() {
        // US25 evalúa un lote nuevo; US28 reevalúa uno con falla enviado a reevaluación
        UUID batch;
        if ("us28".equals(world.story)) {
            batch = failedBatchWithOpenFailure();
            sendReevaluation();
        } else {
            batch = seedBatch("NOT_REVIEWED", false, false);
        }
        UUID control = controlInProgress(batch, "CONFORMING", "CONFORMING");
        world.pending = () -> finish(control);
    }

    @Dado("^que una o más pruebas tienen resultado Con falla$")
    public void someTestsFailed() {
        UUID batch = seedBatch("NOT_REVIEWED", false, false);
        UUID control = controlInProgress(batch, "CONFORMING", "FAILED");
        world.pending = () -> finish(control);
    }

    @Cuando("^finaliza el control$")
    public void finishesTheControl() {
        world.submit();
    }

    @Entonces("^registra fecha y hora de finalización$")
    public void recordsTheEndDateAndTime() {
        assertThat(world.last.status()).as("finalizar el control: %s", world.last.raw()).isEqualTo(200);
        assertThat(world.last.field("control").path("status").asString()).isEqualTo("FINISHED");
        assertWithinTheRequest(instantOf("end_date", world.control));
    }

    @Entonces("^devuelve la fase del lote a Disponible$")
    public void returnsThePhaseToAvailable() {
        // La fase la gobierna Production: Quality le manda RELEASE con el id del control, y Production confirmó (200 del finalizar)
        assertThat(world.last.status()).as("finalizar el control: %s", world.last.raw()).isEqualTo(200);
        assertThat(commandOf(world.batch, "RELEASE").path("requestId").asString()).isEqualTo(world.control.toString());
    }

    @Y("^establece su calidad como Conforme\\.$")
    public void setsTheQualityToConforming() {
        assertThat(projection(world.batch)).containsEntry("quality_status", "CONFORMING").containsEntry("quality_hold", false).containsEntry("ever_controlled", true);
        assertThat(commandOf(world.batch, "RELEASE").path("qualityHold").asBoolean(true)).isFalse();
        assertThat(world.last.field("control").path("overallResult").asString()).isEqualTo("CONFORMING");
    }

    @Y("^establece su calidad como Con falla$")
    public void setsTheQualityToFailed() {
        assertThat(projection(world.batch)).containsEntry("quality_status", "FAILED").containsEntry("ever_controlled", true);
        assertThat(world.last.field("control").path("overallResult").asString()).isEqualTo("FAILED");
    }

    @Y("^genera automáticamente una Falla$")
    public void generatesAFailure() {
        JsonNode failure = world.last.field("failure");
        assertThat(failure.isNull()).isFalse();
        assertThat(failure.path("status").asString()).isEqualTo("OPEN");
        assertThat(failure.path("batchId").asString()).isEqualTo(world.batch.toString());
        assertThat(failure.path("controlId").asString()).isEqualTo(world.control.toString());
        assertThat(count("SELECT count(*) FROM quality_failure WHERE batch_id = ?", world.batch)).isEqualTo(1);
    }

    @Y("^mantiene el lote bloqueado\\.$")
    public void keepsTheBatchBlocked() {
        assertThat(projection(world.batch)).containsEntry("quality_hold", true);
        // Si el escenario finalizó un control, Production recibió RELEASE con la retención; si solo inició la revisión, el lote conserva la suya
        commandsFor(world.batch).stream().filter(c -> "RELEASE".equals(c.path("operation").asString()))
                .forEach(release -> assertThat(release.path("qualityHold").asBoolean()).as("la retención que viaja en RELEASE").isTrue());
        assertThat(commandsFor(world.batch)).noneMatch(c -> "RELEASE_HOLD".equals(c.path("operation").asString()));
    }

    // ---- US28: reevaluación ----

    @Entonces("^el lote cambia a Conforme y queda desbloqueado$")
    public void theBatchIsConformingAndUnblocked() {
        assertThat(world.last.status()).as("finalizar la reevaluación: %s", world.last.raw()).isEqualTo(200);
        assertThat(projection(world.batch)).containsEntry("quality_status", "CONFORMING").containsEntry("quality_hold", false);
        assertThat(commandOf(world.batch, "RELEASE").path("qualityHold").asBoolean(true)).isFalse();
    }

    @Y("^la Falla se registra como Resuelta\\.$")
    public void theFailureIsResolved() {
        assertThat(failureRow(world.failure)).containsEntry("status", "RESOLVED");
        assertThat(world.last.field("failure").path("status").asString()).isEqualTo("RESOLVED");
    }

    @Dado("^que una o más pruebas vuelven a fallar$")
    public void someTestsFailAgain() {
        UUID batch = failedBatchWithOpenFailure();
        sendReevaluation();
        UUID control = controlInProgress(batch, "FAILED", "CONFORMING");
        world.pending = () -> finish(control);
    }

    @Entonces("^el lote vuelve a Con falla$")
    public void theBatchIsFailedAgain() {
        assertThat(world.last.status()).as("finalizar la reevaluación: %s", world.last.raw()).isEqualTo(200);
        assertThat(projection(world.batch)).containsEntry("quality_status", "FAILED").containsEntry("quality_hold", true);
    }

    @Y("^queda pendiente de una nueva decisión administrativa\\.$")
    public void awaitsANewAdministrativeDecision() {
        // Se reutiliza la misma falla, abierta, apuntando al último control; las pruebas del control anterior se conservan
        assertThat(failureRow(world.failure)).containsEntry("status", "OPEN").containsEntry("control_id", world.control);
        assertThat(count("SELECT count(*) FROM quality_failure WHERE batch_id = ?", world.batch)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM quality_test WHERE control_id <> ? AND control_id IN (SELECT id FROM quality_control WHERE batch_id = ?)", world.control,
                world.batch)).isGreaterThanOrEqualTo(1);
    }

    // ---- US34: revisar una posible falla derivada ----

    @Dado("^que el lote tiene calidad Posible falla derivada y nunca fue controlado$")
    public void derivedFailureNeverControlled() {
        UUID batch = seedBatch("POTENTIAL_DERIVED_FAILURE", true, false);
        world.pending = () -> startControl(batch);
    }

    @Dado("^que el lote posee controles anteriores$")
    public void theBatchHasPriorControls() {
        UUID batch = seedBatch("POTENTIAL_DERIVED_FAILURE", true, true);
        world.pending = () -> startControl(batch);
    }

    @Cuando("^inicia su revisión$")
    public void startsItsReview() {
        world.submit();
    }

    @Cuando("^inicia la revisión$")
    public void startsTheReview() {
        world.submit();
    }

    @Entonces("^el sistema permite una Evaluación$")
    public void theSystemAllowsAnEvaluation() {
        assertThat(world.last.status()).as("iniciar la revisión: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("controlType")).isEqualTo("EVALUATION");
    }

    @Entonces("^el sistema permite una Reevaluación$")
    public void theSystemAllowsAReevaluation() {
        assertThat(world.last.status()).as("iniciar la revisión: %s", world.last.raw()).isEqualTo(201);
        assertThat(world.last.text("controlType")).isEqualTo("REEVALUATION");
    }

    @Dado("^que todas las pruebas resultan Conformes$")
    public void allTheReviewTestsAreConforming() {
        UUID batch = seedBatch("POTENTIAL_DERIVED_FAILURE", true, false);
        UUID control = controlInProgress(batch, "CONFORMING");
        world.pending = () -> finish(control);
    }

    @Dado("^que una o más pruebas presentan falla$")
    public void someReviewTestsFail() {
        UUID batch = seedBatch("POTENTIAL_DERIVED_FAILURE", true, false);
        UUID control = controlInProgress(batch, "FAILED");
        world.pending = () -> finish(control);
    }

    @Entonces("^el lote cambia a Conforme$")
    public void theBatchChangesToConforming() {
        assertThat(world.last.status()).as("finalizar la revisión: %s", world.last.raw()).isEqualTo(200);
        assertThat(projection(world.batch)).containsEntry("quality_status", "CONFORMING");
    }

    @Y("^queda desbloqueado\\.$")
    public void isUnblocked() {
        assertThat(projection(world.batch)).containsEntry("quality_hold", false);
        assertThat(commandOf(world.batch, "RELEASE").path("qualityHold").asBoolean(true)).isFalse();
    }

    @Entonces("^el lote cambia a Con falla$")
    public void theBatchChangesToFailed() {
        assertThat(world.last.status()).as("finalizar la revisión: %s", world.last.raw()).isEqualTo(200);
        assertThat(projection(world.batch)).containsEntry("quality_status", "FAILED").containsEntry("quality_hold", true);
    }

    @Y("^genera su propia Falla\\.$")
    public void generatesItsOwnFailure() {
        JsonNode failure = world.last.field("failure");
        assertThat(failure.isNull()).isFalse();
        assertThat(failure.path("controlId").asString()).isEqualTo(world.control.toString());
        assertThat(failure.path("status").asString()).isEqualTo("OPEN");
        assertThat(count("SELECT count(*) FROM quality_failure WHERE batch_id = ? AND control_id = ?", world.batch, world.control)).isEqualTo(1);
    }
}
