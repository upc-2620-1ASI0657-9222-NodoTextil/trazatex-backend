package com.nodotextil.trazatex.quality.integration;

import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;
import com.nodotextil.trazatex.quality.application.port.out.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US44 de punta a punta: el reporte de un control finalizado es un documento para compartir (sin observaciones internas, sin la falla, sin la identidad de
 * nadie), solo lo pide el administrador de la empresa del control, no se almacena y siempre lleva {@code Cache-Control: no-store}; si el generador falla, 503
 * y se puede reintentar.
 */
@SpringBootTest(properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false",
        "trazatex.production.read-timeout=PT0.5S", "trazatex.production.connect-timeout=PT0.5S", "trazatex.production.retry.wait-duration=PT0.01S"})
class ControlReportApiIT extends FlowTestBase {

    static final String SECRET_OBSERVATION = "SECRETO-INTERNO-DEL-CONTROL";
    static final String SECRET_CAUSE = "CAUSA-PRIVADA-DE-LA-FALLA";
    static final String SECRET_FAILURE_NOTES = "NOTAS-PRIVADAS-DE-LA-FALLA";
    static final String TRACEABILITY_ID = "TT-REPORTE001";

    @MockitoSpyBean
    PdfGeneratorPort pdfSpy;

    /** Un lote con Traceability ID cuyo control (con observaciones internas y una prueba con falla) lo inició ADMIN_A y lo finalizó OTRO administrador. */
    UUID finishedControl() throws Exception {
        UUID batch = UUID.randomUUID();
        projections.insert(BatchProjection.restore(batch, COMPANY_A, BatchPhase.AVAILABLE, false, QualityStatus.NOT_REVIEWED, false, 1, START, TRACEABILITY_ID));
        ProductionStub.reset();
        ProductionStub.answersOk();
        UUID control = UUID.fromString(read(mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quality/batches/" + batch + "/controls")
                .contentType(MediaType.APPLICATION_JSON).content("{\"internalObservations\":\"" + SECRET_OBSERVATION + "\"}"), ADMIN_OF_A)).andExpect(status().isCreated()))
                .path("id").asString());
        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Resistencia a la tracción", "expectedValue", "50",
                "obtainedValue", "52,3", "unit", "N", "testResult", "CONFORMING", "remarks", "dentro del rango")).andExpect(status().isCreated());
        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/tests", Map.of("criterionName", "Humedad", "expectedValue", "8", "obtainedValue", "15",
                "unit", "%", "testResult", "FAILED", "remarks", "muy húmedo")).andExpect(status().isCreated());
        postAs(OTHER_ADMIN_OF_A, "/api/quality/controls/" + control + "/finish").andExpect(status().isOk());
        UUID failure = failureOfBatch(batch);
        putAs(OTHER_ADMIN_OF_A, "/api/quality/failures/" + failure, Map.of("rootCause", SECRET_CAUSE, "affectedQtyKg", 12.5, "observations", SECRET_FAILURE_NOTES))
                .andExpect(status().isOk());
        ProductionStub.reset();
        return control;
    }

    static String text(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    // ---- US44 esc. 1 ----

    @Test
    void aFinishedControlProducesAShareableDocumentWithTheTestsValuesResultsAndRemarks_US44Scenario1() throws Exception {
        UUID control = finishedControl();

        MvcResult result = getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("attachment"),
                        org.hamcrest.Matchers.containsString(TRACEABILITY_ID), org.hamcrest.Matchers.containsString(".pdf")))).andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(result.getResponse().getContentLength()).isEqualTo(pdf.length);
        assertThat(text(pdf)).contains(TRACEABILITY_ID).contains(control.toString()).contains("Evaluación").contains("Con falla")
                .contains("Resistencia a la tracción").contains("52,3").contains("dentro del rango")
                .contains("Humedad").contains("muy húmedo").contains("23/10/2026 07:00");
    }

    @Test
    void theSharedDocumentNeverCarriesTheInternalObservationsTheFailureOrAnyoneIdentity() throws Exception {
        UUID control = finishedControl();

        String content = text(getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(content).doesNotContain(SECRET_OBSERVATION).doesNotContain(SECRET_CAUSE).doesNotContain(SECRET_FAILURE_NOTES)
                .doesNotContain(ADMIN_A.toString()).doesNotContain(OTHER_ADMIN_A.toString()).doesNotContain(COMPANY_A.toString()).doesNotContainIgnoringCase("inspector")
                .doesNotContain("12.5").doesNotContain("12,5");
    }

    @Test
    void theDocumentIsNotStoredAnywhereOnlyTheAuditRowRemains() throws Exception {
        UUID control = finishedControl();
        List<String> tables = List.of("quality_control", "quality_test", "quality_failure", "impacted_batch", "batch_projection", "outbox", "evidence", "evidence_upload_queue",
                "idempotency_keys");
        List<Long> before = tables.stream().map(this::count).toList();
        long auditBefore = count("audit_log");

        getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk());
        getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk());

        assertThat(tables.stream().map(this::count).toList()).as("ninguna tabla guarda el documento").isEqualTo(before);
        assertThat(count("audit_log") - auditBefore).as("solo la auditoría de cada generación").isEqualTo(2);
        assertThat(jdbc.sql("SELECT user_id, entity_id, changes::text AS changes FROM audit_log WHERE action = 'REPORT_GENERATED' ORDER BY occurred_at DESC LIMIT 1")
                .query().singleRow()).containsEntry("user_id", ADMIN_A).containsEntry("entity_id", control.toString());
        assertThat(jdbc.sql("SELECT changes::text FROM audit_log WHERE action = 'REPORT_GENERATED' LIMIT 1").query(String.class).single()).doesNotContain("%PDF");
    }

    @Test
    void aRowFromBeforeV4WithoutTraceabilityIdShowsTheBatchUuid() throws Exception {
        UUID control = finishedControl();
        UUID batch = jdbc.sql("SELECT batch_id FROM quality_control WHERE id = :c").param("c", control).query(UUID.class).single();
        jdbc.sql("UPDATE batch_projection SET traceability_id = NULL WHERE batch_id = :b").param("b", batch).update();

        MvcResult result = getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(batch.toString()))).andReturn();

        assertThat(text(result.getResponse().getContentAsByteArray())).contains(batch.toString()).doesNotContain(TRACEABILITY_ID);
    }

    // ---- solo un control finalizado ----

    @Test
    void aControlThatIsNotFinishedIsA409WithNoStoreWhateverItsState() throws Exception {
        UUID inProgress = startControl(seedAvailable());
        addTest(inProgress, "CONFORMING");
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        UUID finishPending = startControlWithProductionUp();
        addTest(finishPending, "CONFORMING");
        ProductionStub.answers(503, ProductionStub.apiError("TEMPORARILY_UNAVAILABLE"));
        postAs(ADMIN_OF_A, "/api/quality/controls/" + finishPending + "/finish").andExpect(status().isAccepted());
        productionBreaker.reset();
        UUID starting = UUID.fromString(read(postAs(ADMIN_OF_A, "/api/quality/batches/" + seedAvailable() + "/controls").andExpect(status().isAccepted())).path("id").asString());
        ProductionStub.reset();
        ProductionStub.answers(409, ProductionStub.apiError("COMPANY_INACTIVE"));
        productionBreaker.reset();
        UUID cancelledBatch = seedAvailable();
        postAs(ADMIN_OF_A, "/api/quality/batches/" + cancelledBatch + "/controls").andExpect(status().isConflict());
        UUID cancelled = jdbc.sql("SELECT id FROM quality_control WHERE batch_id = :b").param("b", cancelledBatch).query(UUID.class).single();

        for (UUID control : List.of(inProgress, finishPending, starting, cancelled)) {
            getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTROL_NOT_FINISHED")).andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    private UUID startControlWithProductionUp() throws Exception {
        productionBreaker.reset();
        return startControl(seedAvailable());
    }

    // ---- quién puede pedirlo ----

    @Test
    void anotherCompanyAnOperatorAndAnAnonymousCallerNeverGetTheDocumentAndEveryAnswerIsNoStore() throws Exception {
        UUID control = finishedControl();
        String missing = getAs(ADMIN_OF_B, "/api/quality/controls/" + UUID.randomUUID() + "/report").andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        String foreign = getAs(ADMIN_OF_B, "/api/quality/controls/" + control + "/report").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTROL_NOT_FOUND"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(foreign).path("message")).isEqualTo(json.readTree(missing).path("message"));
        assertThat(json.readTree(foreign).path("code")).isEqualTo(json.readTree(missing).path("code"));
        getAs(OPERATOR_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/quality/controls/" + control + "/report")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        getAs(ADMIN_OF_A, "/api/quality/controls/no-es-uuid/report").andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(count("audit_log") - jdbc.sql("SELECT count(*) FROM audit_log WHERE action <> 'REPORT_GENERATED'").query(Long.class).single()).as("ninguna de estas peticiones auditó un reporte").isZero();
    }

    // ---- US44 esc. 2: el generador falla ----

    @Test
    void whenTheGeneratorFailsThereIsAn503WithItsOwnCodeNothingIsChangedAndRetryingWorks_US44Scenario2() throws Exception {
        UUID control = finishedControl();
        long auditBefore = count("audit_log");
        Mockito.doThrow(new PdfGenerationException("el generador no responde")).doCallRealMethod().when(pdfSpy).render(any());

        getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("REPORT_UNAVAILABLE"))
                .andExpect(header().string("Retry-After", "5")).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Intenta de nuevo")));

        assertThat(count("audit_log")).as("una generación fallida no deja auditoría").isEqualTo(auditBefore);
        MvcResult retried = getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isOk()).andReturn();
        assertThat(new String(retried.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(count("audit_log")).isEqualTo(auditBefore + 1);
    }

    @Test
    void aTimeoutOrAnUnexpectedErrorInTheGeneratorIsTheSame503() throws Exception {
        UUID control = finishedControl();
        Mockito.doThrow(new PdfGenerationException("La generación del reporte superó PT10S", new java.util.concurrent.TimeoutException())).when(pdfSpy).render(any());

        getAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/report").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("REPORT_UNAVAILABLE"));
    }

}
