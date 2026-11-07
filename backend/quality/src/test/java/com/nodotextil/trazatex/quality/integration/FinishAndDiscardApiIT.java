package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.ControlStatus;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US24…US28 y US34 de punta a punta: finalizar y descartar cambian el estado SOLO con la confirmación de Production (RELEASE / DISCARD); sin
 * respuesta quedan pendientes y los reintenta el job con el mismo requestId; los eventos salen una sola vez y en la misma transacción que el cambio.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false",
        "trazatex.production.read-timeout=PT0.5S", "trazatex.production.connect-timeout=PT0.5S",
        "trazatex.production.retry.wait-duration=PT0.01S",
        "trazatex.quality.start.claims-per-run=3", "trazatex.quality.start.error-after-attempts=3"})
@ExtendWith(OutputCaptureExtension.class)
class FinishAndDiscardApiIT extends FlowTestBase {

    @MockitoSpyBean
    QualityControlRepositoryPort controlsSpy;

    // ---- ayudas ----

    /** Un control en curso con una prueba con el resultado dado. */
    UUID controlWith(UUID batch, String... results) throws Exception {
        UUID control = startControl(batch);
        for (String result : results) {
            addTest(control, result);
        }
        return control;
    }

    ResultActionsWrapper finish(UUID control) throws Exception {
        return new ResultActionsWrapper(postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/finish"));
    }

    record ResultActionsWrapper(org.springframework.test.web.servlet.ResultActions actions) {
    }

    /**
     * {@code QualityFailureUpdated} (agregado en V2): tópico {@code quality.failure}, clave {@code failureId} y exactamente estos seis campos, cada uno
     * con su valor ({@code cause} y {@code affectedQuantityKg} van en {@code null} mientras la falla no se completa).
     */
    static void assertFailureUpdated(Outboxed event, UUID failure, UUID batch, String cause, String affectedQuantityKg, String status) {
        assertThat(event.type()).isEqualTo("QualityFailureUpdated");
        assertThat(event.topic()).isEqualTo("quality.failure");
        assertThat(event.key()).isEqualTo(failure.toString());
        List<String> fields = new ArrayList<>();
        event.payload().propertyNames().forEach(fields::add);
        assertThat(fields).as("exactamente los campos del catálogo")
                .containsExactlyInAnyOrder("failureId", "batchId", "companyId", "cause", "affectedQuantityKg", "status");
        assertThat(event.payload().path("failureId").asString()).isEqualTo(failure.toString());
        assertThat(event.payload().path("batchId").asString()).isEqualTo(batch.toString());
        assertThat(event.payload().path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        if (cause == null) {
            assertThat(event.payload().path("cause").isNull()).as("cause en null").isTrue();
        } else {
            assertThat(event.payload().path("cause").asString()).isEqualTo(cause);
        }
        if (affectedQuantityKg == null) {
            assertThat(event.payload().path("affectedQuantityKg").isNull()).as("affectedQuantityKg en null").isTrue();
        } else {
            assertThat(event.payload().path("affectedQuantityKg").decimalValue()).isEqualByComparingTo(affectedQuantityKg);
        }
        assertThat(event.payload().path("status").asString()).isEqualTo(status);
    }

    /** Un lote con un control que falló y su falla {@code OPEN}, todo confirmado por Production. */
    UUID failureOf(UUID batch) throws Exception {
        UUID control = controlWith(batch, "FAILED");
        ProductionStub.answersOk();
        finish(control).actions().andExpect(status().isOk());
        ProductionStub.reset();
        return failureOfBatch(batch);
    }

    void completeInfo(UUID failure) throws Exception {
        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", "Humedad excesiva", "affectedQtyKg", new BigDecimal("12.5"),
                "observations", "lote húmedo")).andExpect(status().isOk());
    }

    // ---- US24: pruebas ----

    @Test
    void aTestIsKeptAndTheOtherCompanyOrAnOperatorCannotAddOne() throws Exception {
        UUID batch = seedAvailable();
        UUID control = startControl(batch);

        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", " Resistencia ", "expectedValue", "50", "obtainedValue", "52",
                "unit", "N", "testResult", "CONFORMING", "remarks", "ok")).andExpect(status().isCreated()).andExpect(jsonPath("$.criterionName").value("Resistencia"))
                .andExpect(jsonPath("$.testResult").value("CONFORMING"));

        assertThat(jdbc.sql("SELECT count(*) FROM quality_test WHERE control_id = :c").param("c", control).query(Long.class).single()).isEqualTo(1);
        assertThat(auditActions()).contains("TEST_ADDED");
        postAs(ADMIN_OF_B, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "x")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTROL_NOT_FOUND"));
        postAs(OPERATOR_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "x")).andExpect(status().isForbidden());
    }

    @Test
    void anIncompleteTestIsRefusedAndNothingIsKept() throws Exception {
        UUID control = startControl(seedAvailable());

        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Humedad", "unit", "%")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELDS"));

        assertThat(jdbc.sql("SELECT count(*) FROM quality_test").query(Long.class).single()).isZero();
    }

    @Test
    void testsCannotBeAddedToAControlThatIsNotInProgress() throws Exception {
        UUID batch = seedAvailable();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID starting = UUID.fromString(read(postAs(ADMIN_OF_A, "/api/quality/batches/" + batch + "/controls").andExpect(status().isAccepted())).path("id").asString());

        postAs(ADMIN_OF_A, "/api/quality/controls/" + starting + "/tests", Map.of("criterionName", "Humedad", "expectedValue", "8", "obtainedValue", "7",
                "unit", "%", "testResult", "CONFORMING")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROL_NOT_IN_PROGRESS"));
    }

    // ---- US25 / US28: finalizar con la confirmación de Production ----

    @Test
    void finishingConformingReleasesTheBatchAndEmitsTheEventsOnceInTheSameTransaction_US25Scenario1() throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "CONFORMING", "CONFORMING");
        ProductionStub.answersOk();

        JsonNode result = read(finish(control).actions().andExpect(status().isOk()));

        assertThat(result.path("control").path("status").asString()).isEqualTo("FINISHED");
        assertThat(result.path("control").path("overallResult").asString()).isEqualTo("CONFORMING");
        assertThat(result.path("failure").isNull()).isTrue();
        assertThat(control(control, "status", String.class)).isEqualTo("FINISHED");
        assertThat(at(control(control, "end_date", OffsetDateTime.class))).isEqualTo(START);
        assertThat(control(control, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(projection(batch).qualityHold()).isFalse();
        assertThat(projection(batch).everControlled()).isTrue();
        JsonNode release = sentBody(sent(batch).get(0));
        assertThat(release.path("operation").asString()).isEqualTo("RELEASE");
        assertThat(release.path("requestId").asString()).isEqualTo(control.toString());
        assertThat(release.path("qualityHold").asBoolean(true)).isFalse();
        assertThat(release.path("requestedBy").asString()).isEqualTo(ADMIN_A.toString());
        assertThat(outboxTypes()).containsExactly("QualityControlCompleted", "QualityStatusChanged");
        assertThat(outbox().get(0).topic()).isEqualTo("quality.control");
        assertThat(outbox().get(0).key()).isEqualTo(control.toString());
        assertThat(outbox().get(0).payload().path("result").asString()).isEqualTo("CONFORMING");
        assertThat(outbox().get(1).key()).isEqualTo(batch.toString());
        assertThat(outbox().get(1).payload().path("previousStatus").asString()).isEqualTo("NOT_REVIEWED");
        assertThat(outbox().get(1).payload().path("newStatus").asString()).isEqualTo("CONFORMING");
        assertThat(auditActions()).containsExactly("CONTROL_FINISHED", "CONTROL_STARTED", "TEST_ADDED", "TEST_ADDED");
        assertThat(jdbc.sql("SELECT count(*) FROM quality_failure").query(Long.class).single()).isZero();
    }

    @Test
    void finishingWithAFailedTestKeepsTheBatchOnHoldAndGeneratesItsFailure_US25Scenario2() throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "CONFORMING", "FAILED");
        ProductionStub.answersOk();

        JsonNode result = read(finish(control).actions().andExpect(status().isOk()));

        assertThat(sentBody(sent(batch).get(0)).path("qualityHold").asBoolean()).isTrue();
        assertThat(result.path("control").path("overallResult").asString()).isEqualTo("FAILED");
        UUID failure = failureOfBatch(batch);
        assertThat(result.path("failure").path("id").asString()).isEqualTo(failure.toString());
        assertThat(result.path("failure").path("status").asString()).isEqualTo("OPEN");
        assertThat(failure(failure, "control_id", UUID.class)).isEqualTo(control);
        assertThat(failure(failure, "company_id", UUID.class)).isEqualTo(COMPANY_A);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(projection(batch).qualityHold()).isTrue();
        assertThat(outboxTypes()).as("la falla generada sale antes que el control y el estado")
                .containsExactly("QualityFailureUpdated", "QualityControlCompleted", "QualityStatusChanged");
        assertFailureUpdated(outbox().get(0), failure, batch, null, null, "OPEN");
        assertThat(outbox().get(2).payload().path("newStatus").asString()).isEqualTo("FAILED");
        assertThat(auditActions()).contains("CONTROL_FINISHED", "FAILURE_GENERATED");
    }

    @Test
    void withoutAnAnswerTheFinishStaysPendingNothingChangesNoEventIsEmittedAndTheJobFinishesItWithTheSameRequestId() throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "FAILED");
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));

        JsonNode pending = read(finish(control).actions().andExpect(status().isAccepted()));

        assertThat(pending.path("control").path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(pending.path("control").path("finishPending").asBoolean()).isTrue();
        assertThat(control(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(control(control, "finish_result", String.class)).isEqualTo("FAILED");
        assertThat(control(control, "attempts", Integer.class)).isEqualTo(1);
        assertThat(at(control(control, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START.plusSeconds(15));
        assertThat(projection(batch).qualityStatus()).as("la calidad no cambia sin confirmación").isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(projection(batch).everControlled()).isFalse();
        assertThat(outbox()).as("ningún evento sin confirmación").isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM quality_failure").query(Long.class).single()).isZero();
        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Otra", "expectedValue", "1", "obtainedValue", "1", "unit", "u",
                "testResult", "CONFORMING")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROL_FINISH_PENDING"));
        int calls = sent(batch).size();
        finish(control).actions().andExpect(status().isAccepted());
        assertThat(sent(batch)).as("pedirlo otra vez no manda otro comando").hasSize(calls);

        ProductionStub.reset();
        ProductionStub.answersOk();
        clock.advance(Duration.ofSeconds(15));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(control(control, "status", String.class)).isEqualTo("FINISHED");
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(outboxTypes()).containsExactly("QualityFailureUpdated", "QualityControlCompleted", "QualityStatusChanged");
        assertFailureUpdated(outbox().get(0), failureOfBatch(batch), batch, null, null, "OPEN");
        assertThat(sent(batch)).hasSize(1);
        assertThat(sentBody(sent(batch).get(0)).path("requestId").asString()).isEqualTo(control.toString());
        assertThat(sentBody(sent(batch).get(0)).path("qualityHold").asBoolean()).as("la misma retención que la primera vez").isTrue();
        assertThat(recovery.recoverAll()).isZero();
        assertThat(outbox()).as("los eventos salen una sola vez").hasSize(3);
        assertThat(jdbc.sql("SELECT count(*) FROM quality_failure WHERE batch_id = :b").param("b", batch).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void ifQualityDiesBetweenReleaseAndSavingTheJobRepeatsTheSameCommandAndTheEventsStillLeaveOnce() throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "CONFORMING");
        ProductionStub.answersOk();
        // 1.ª lectura con bloqueo: la solicitud (transacción A); 2.ª: aplicar la respuesta (transacción B), que «cae»
        Mockito.doCallRealMethod().doThrow(new IllegalStateException("el proceso cayó antes de guardar la respuesta")).doCallRealMethod()
                .when(controlsSpy).findByIdForUpdate(any());

        finish(control).actions().andExpect(status().isInternalServerError());

        assertThat(control(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(control(control, "finish_result", String.class)).isEqualTo("CONFORMING");
        assertThat(sent(batch)).as("Production SÍ aplicó RELEASE").hasSize(1);
        assertThat(outbox()).isEmpty();
        assertThat(recovery.recoverPending()).as("dentro de la gracia la petición sigue siendo la dueña").isZero();

        clock.advance(Duration.ofSeconds(31));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(control(control, "status", String.class)).isEqualTo("FINISHED");
        assertThat(outboxTypes()).containsExactly("QualityControlCompleted", "QualityStatusChanged");
        assertThat(sent(batch).stream().map(r -> sentBody(r).path("requestId").asString())).containsExactly(control.toString(), control.toString());
    }

    @Test
    void aRejectedReleaseLeavesTheControlInProgressWithoutEventsAndTheTestsOpenAgain(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "CONFORMING");
        ProductionStub.answers(409, ProductionStub.apiError("INVALID_PHASE_TRANSITION"));

        finish(control).actions().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PHASE_TRANSITION"));

        assertThat(control(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(control(control, "finish_requested_at", OffsetDateTime.class)).as("la finalización pendiente se descarta").isNull();
        assertThat(control(control, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.NOT_REVIEWED);
        assertThat(outbox()).isEmpty();
        assertThat(sent(batch)).as("un 409 no se reintenta").hasSize(1);
        assertThat(auditActions()).contains("CONTROL_FINISH_REJECTED");
        assertThat(output.getAll()).contains("ERROR").contains("RELEASE o DISCARD rechazado");
        assertThat(recovery.recoverAll()).as("no queda nada que reintentar").isZero();
        addTest(control, "CONFORMING");
        ProductionStub.reset();
        ProductionStub.answersOk();
        finish(control).actions().andExpect(status().isOk());
        assertThat(control(control, "status", String.class)).isEqualTo("FINISHED");
    }

    @Test
    void aReleaseOnABatchProductionDoesNotKnowIsA404AndABadRequestIsADefect() throws Exception {
        UUID batch = seedAvailable();
        UUID control = controlWith(batch, "CONFORMING");
        ProductionStub.answers(404, ProductionStub.apiError("BATCH_NOT_FOUND"));
        finish(control).actions().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BATCH_NOT_FOUND"));
        assertThat(control(control, "status", String.class)).isEqualTo("IN_PROGRESS");

        ProductionStub.reset();
        ProductionStub.answers(400, ProductionStub.apiError("MISSING_FIELDS"));
        finish(control).actions().andExpect(status().isInternalServerError());
        assertThat(control(control, "status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(control(control, "finish_requested_at", OffsetDateTime.class)).isNull();
        assertThat(outbox()).isEmpty();
    }

    @Test
    void aControlWithoutTestsCannotBeFinishedAndAFinishedOneCannotBeFinishedAgain() throws Exception {
        UUID batch = seedAvailable();
        UUID control = startControl(batch);
        ProductionStub.answersOk();

        finish(control).actions().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROL_HAS_NO_TESTS"));
        assertThat(sent(batch)).isEmpty();

        addTest(control, "CONFORMING");
        finish(control).actions().andExpect(status().isOk());
        finish(control).actions().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROL_NOT_IN_PROGRESS"));
        assertThat(sent(batch)).hasSize(1);
        assertThat(outbox()).hasSize(2);
    }

    @Test
    void onlyAnAdminOfTheCompanyFinishesAControl() throws Exception {
        UUID control = controlWith(seedAvailable(), "CONFORMING");

        postAs(ADMIN_OF_B, "/api/quality/controls/" + control + "/finish").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTROL_NOT_FOUND"));
        postAs(OPERATOR_OF_A, "/api/quality/controls/" + control + "/finish").andExpect(status().isForbidden());
        assertThat(control(control, "finish_requested_at", OffsetDateTime.class)).isNull();
    }

    // ---- US28: reevaluación ----

    @Test
    void aReevaluationThatPassesResolvesTheFailureAndReleasesTheBatch_US28Scenario1() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isOk());
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.PENDING_REEVALUATION);
        UUID second = controlWith(batch, "CONFORMING");
        assertThat(control(second, "control_type", String.class)).isEqualTo("REEVALUATION");
        ProductionStub.answersOk();

        read(finish(second).actions().andExpect(status().isOk()));

        assertThat(failure(failure, "status", String.class)).isEqualTo("RESOLVED");
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(projection(batch).qualityHold()).isFalse();
        assertThat(sentBody(sent(batch).get(0)).path("qualityHold").asBoolean(true)).isFalse();
        assertThat(auditActions()).contains("FAILURE_RESOLVED");
        assertThat(jdbc.sql("SELECT count(*) FROM quality_failure WHERE batch_id = :b").param("b", batch).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void aReevaluationThatFailsAgainReusesTheSameFailurePointsItToTheLatestControlAndKeepsTheOldTests_US28Scenario2() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        UUID firstControl = failure(failure, "control_id", UUID.class);
        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isOk());
        UUID second = controlWith(batch, "FAILED", "CONFORMING");
        ProductionStub.answersOk();

        JsonNode result = read(finish(second).actions().andExpect(status().isOk()));

        assertThat(result.path("failure").path("id").asString()).as("no se crea otra falla").isEqualTo(failure.toString());
        assertThat(failure(failure, "status", String.class)).isEqualTo("OPEN");
        assertThat(failure(failure, "control_id", UUID.class)).isEqualTo(second);
        assertThat(jdbc.sql("SELECT count(*) FROM quality_failure WHERE batch_id = :b").param("b", batch).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM quality_test WHERE control_id = :c").param("c", firstControl).query(Long.class).single()).as("pruebas del primer control").isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM quality_test WHERE control_id = :c").param("c", second).query(Long.class).single()).as("pruebas del segundo").isEqualTo(2);
        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(projection(batch).qualityHold()).isTrue();
        assertThat(auditActions()).contains("FAILURE_REOPENED");
    }

    // ---- US34: revisión de una posible falla derivada ----

    @Test
    void aDerivedFailureReviewWithoutPriorControlIsAnEvaluationAndConformingUnblocksTheBatch_US34Scenarios1And3() throws Exception {
        UUID batch = seed(BatchPhase.AVAILABLE, QualityStatus.POTENTIAL_DERIVED_FAILURE, true, false);
        UUID control = controlWith(batch, "CONFORMING");
        assertThat(control(control, "control_type", String.class)).isEqualTo("EVALUATION");
        ProductionStub.answersOk();

        finish(control).actions().andExpect(status().isOk());

        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.CONFORMING);
        assertThat(projection(batch).qualityHold()).isFalse();
        assertThat(sentBody(sent(batch).get(0)).path("qualityHold").asBoolean(true)).isFalse();
    }

    @Test
    void aDerivedFailureReviewWithPriorControlsIsAReevaluationAndFailingGeneratesItsOwnFailure_US34Scenarios2And4() throws Exception {
        UUID batch = seed(BatchPhase.AVAILABLE, QualityStatus.POTENTIAL_DERIVED_FAILURE, true, true);
        UUID control = controlWith(batch, "FAILED");
        assertThat(control(control, "control_type", String.class)).isEqualTo("REEVALUATION");
        ProductionStub.answersOk();

        JsonNode result = read(finish(control).actions().andExpect(status().isOk()));

        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(result.path("failure").path("status").asString()).isEqualTo("OPEN");
        assertThat(failure(UUID.fromString(result.path("failure").path("id").asString()), "control_id", UUID.class)).isEqualTo(control);
    }

    // ---- US26: la información de la falla y enviar a reevaluación ----

    @Test
    void theFailureInfoIsKeptAndValidated_US26Scenario1() throws Exception {
        UUID failure = failureOf(seedAvailable());

        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", " Humedad ", "affectedQtyKg", 12.5, "observations", " lote húmedo "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rootCause").value("Humedad")).andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(failure(failure, "root_cause", String.class)).isEqualTo("Humedad");
        assertThat(failure(failure, "affected_qty_kg", BigDecimal.class)).isEqualByComparingTo("12.5");
        assertThat(auditActions()).contains("FAILURE_UPDATED");
        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", " ", "affectedQtyKg", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELDS"));
        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", "x", "affectedQtyKg", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        getAs(ADMIN_OF_A, "/api/quality/failures/" + failure).andExpect(status().isOk()).andExpect(jsonPath("$.rootCause").value("Humedad"));
    }

    @Test
    void sendingToReevaluationNeedsNothingKeepsTheHoldAndDoesNotCallProduction_US26Scenario2() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        UUID control = failure(failure, "control_id", UUID.class);
        outboxTypes();
        jdbc.sql("DELETE FROM outbox").update();

        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(projection(batch).qualityStatus()).isEqualTo(QualityStatus.PENDING_REEVALUATION);
        assertThat(projection(batch).qualityHold()).isTrue();
        assertThat(sent(batch)).isEmpty();
        assertThat(outbox()).hasSize(1);
        assertThat(outbox().get(0).type()).isEqualTo("QualityStatusChanged");
        assertThat(outbox().get(0).payload().path("previousStatus").asString()).isEqualTo("FAILED");
        assertThat(outbox().get(0).payload().path("newStatus").asString()).isEqualTo("PENDING_REEVALUATION");
        assertThat(outbox().get(0).payload().path("controlId").asString()).isEqualTo(control.toString());
        assertThat(auditActions()).contains("FAILURE_REEVALUATION_STARTED");
        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUALITY_TRANSITION"));
    }

    // ---- US27: descartar con la confirmación de Production ----

    @Test
    void discardingNeedsTheCauseAndTheQuantityBeforeCallingProduction() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        ProductionStub.answersOk();

        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/discard").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAILURE_INFO_INCOMPLETE"));

        assertThat(sent(batch)).isEmpty();
        assertThat(failure(failure, "status", String.class)).isEqualTo("OPEN");
    }

    @Test
    void discardingConfirmsTheFailureAndEmitsFailureConfirmedOnlyWithProductionsConfirmation_US27() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        UUID control = failure(failure, "control_id", UUID.class);
        completeInfo(failure);
        jdbc.sql("DELETE FROM outbox").update();
        ProductionStub.answersOk();

        JsonNode result = read(postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/discard").andExpect(status().isOk()));

        assertThat(result.path("failure").path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(result.path("completed").asBoolean()).isTrue();
        assertThat(failure(failure, "status", String.class)).isEqualTo("CONFIRMED");
        JsonNode discard = sentBody(sent(batch).get(0));
        assertThat(discard.path("operation").asString()).isEqualTo("DISCARD");
        assertThat(discard.path("requestId").asString()).isEqualTo(failure.toString());
        assertThat(discard.path("requestedBy").asString()).isEqualTo(ADMIN_A.toString());
        assertThat(outboxTypes()).containsExactly("FailureConfirmed", "QualityFailureUpdated");
        Outboxed confirmed = outbox().get(0);
        assertThat(confirmed.type()).isEqualTo("FailureConfirmed");
        assertThat(confirmed.topic()).isEqualTo("quality.failure");
        assertThat(confirmed.key()).isEqualTo(failure.toString());
        assertThat(confirmed.payload().path("failureId").asString()).isEqualTo(failure.toString());
        assertThat(confirmed.payload().path("batchId").asString()).isEqualTo(batch.toString());
        assertThat(confirmed.payload().path("controlId").asString()).isEqualTo(control.toString());
        assertThat(confirmed.payload().path("companyId").asString()).isEqualTo(COMPANY_A.toString());
        assertThat(confirmed.payload().path("cause").asString()).isEqualTo("Humedad excesiva");
        assertThat(confirmed.payload().path("affectedQuantityKg").decimalValue()).isEqualByComparingTo("12.5");
        assertFailureUpdated(outbox().get(1), failure, batch, "Humedad excesiva", "12.5", "CONFIRMED");
        assertThat(auditActions()).contains("FAILURE_CONFIRMED");
    }

    @Test
    void withoutAnAnswerTheDiscardStaysPendingTheFailureIsFrozenAndTheJobConfirmsItOnce() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        completeInfo(failure);
        jdbc.sql("DELETE FROM outbox").update();
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));

        JsonNode pending = read(postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/discard").andExpect(status().isAccepted()));

        assertThat(pending.path("failure").path("status").asString()).isEqualTo("OPEN");
        assertThat(pending.path("failure").path("discardPending").asBoolean()).isTrue();
        assertThat(failure(failure, "status", String.class)).isEqualTo("OPEN");
        assertThat(at(failure(failure, "next_attempt_at", OffsetDateTime.class))).isEqualTo(START.plusSeconds(15));
        assertThat(outbox()).as("FailureConfirmed solo con la confirmación").isEmpty();
        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", "otra", "affectedQtyKg", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAILURE_DISCARD_PENDING"));
        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAILURE_DISCARD_PENDING"));

        ProductionStub.reset();
        ProductionStub.answersOk();
        clock.advance(Duration.ofSeconds(15));
        assertThat(recovery.recoverPending()).isEqualTo(1);

        assertThat(failure(failure, "status", String.class)).isEqualTo("CONFIRMED");
        assertThat(outboxTypes()).containsExactly("FailureConfirmed", "QualityFailureUpdated");
        assertFailureUpdated(outbox().get(1), failure, batch, "Humedad excesiva", "12.5", "CONFIRMED");
        assertThat(sentBody(sent(batch).get(0)).path("requestId").asString()).isEqualTo(failure.toString());
        assertThat(recovery.recoverAll()).isZero();
        assertThat(outbox()).as("los eventos salen una sola vez").hasSize(2);
    }

    @Test
    void aRejectedDiscardLeavesTheFailureOpenAndEditableWithoutEvents(CapturedOutput output) throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        completeInfo(failure);
        jdbc.sql("DELETE FROM outbox").update();
        ProductionStub.answers(409, ProductionStub.apiError("INVALID_PHASE_TRANSITION"));

        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/discard").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PHASE_TRANSITION"));

        assertThat(failure(failure, "status", String.class)).isEqualTo("OPEN");
        assertThat(failure(failure, "next_attempt_at", OffsetDateTime.class)).isNull();
        assertThat(failure(failure, "discard_requested_at", OffsetDateTime.class)).isNull();
        assertThat(outbox()).isEmpty();
        assertThat(auditActions()).contains("FAILURE_DISCARD_REJECTED");
        assertThat(output.getAll()).contains("ERROR").contains("RELEASE o DISCARD rechazado");
        putAs(ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", "corregida", "affectedQtyKg", 2)).andExpect(status().isOk());
    }

    @Test
    void onlyAFailedBatchCanBeDiscardedAndAnotherCompanyCannotSeeTheFailure() throws Exception {
        UUID batch = seedAvailable();
        UUID failure = failureOf(batch);
        completeInfo(failure);
        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/reevaluation").andExpect(status().isOk());
        ProductionStub.answersOk();

        postAs(ADMIN_OF_A, "/api/quality/failures/" + failure + "/discard").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUALITY_TRANSITION"));

        assertThat(sent(batch)).isEmpty();
        getAs(ADMIN_OF_B, "/api/quality/failures/" + failure).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAILURE_NOT_FOUND"));
        postAs(ADMIN_OF_B, "/api/quality/failures/" + failure + "/discard").andExpect(status().isNotFound());
        getAs(OPERATOR_OF_A, "/api/quality/failures/" + failure).andExpect(status().isForbidden());
    }
}
