package com.nodotextil.trazatex.production.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import com.nodotextil.trazatex.production.application.port.in.UploadPendingEvidenceUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nodotextil.trazatex.production.domain.SampleImages.jpeg;
import static com.nodotextil.trazatex.production.domain.SampleImages.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-007 con Cloudinary simulado: timeout, 5xx y circuito abierto dejan la evidencia PENDING (la petición termina bien, 201), y el
 * job la sube cuando el servicio vuelve, con espera creciente, sin duplicar y sin consumir intentos cuando no se llegó a intentar.
 * Además, una subida lenta no retiene el lote ni la transformación: una división, una transferencia o una finalización simultáneas
 * no esperan a Cloudinary.
 */
class EvidenceResilienceIT extends EvidenceItBase {

    @Autowired
    UploadPendingEvidenceUseCase pendingUploads;

    private JsonNode items(HttpResponse<String> response) {
        return body(response).path("items");
    }

    private HttpResponse<String> attach(UUID batch, String key, FilePart... files) {
        return postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, files);
    }

    private FilePart photo() {
        return FilePart.files("a.jpg", "image/jpeg", jpeg());
    }

    private Object column(UUID id, String column) {
        return jdbc.sql("SELECT " + column + " FROM evidence WHERE id = :id").param("id", id).query((rs, n) -> rs.getObject(1)).list().get(0);
    }

    private OffsetDateTime timestamp(UUID id, String column) {
        return jdbc.sql("SELECT " + column + " FROM evidence WHERE id = :id").param("id", id)
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class)).list().get(0);
    }

    private UUID idOfItem(JsonNode item) {
        return UUID.fromString(item.path("id").asString());
    }

    private long queued(UUID id) {
        return count("SELECT count(*) FROM evidence_upload_queue WHERE evidence_id = ?", id);
    }

    // ---- la petición termina bien aunque Cloudinary falle ----

    @Test
    void aCloudinaryTimeoutLeavesTheEvidencePendingWithItsBytesInTheDatabaseAndTheRequestStillSucceeds() throws Exception {
        stubUploadDelayed(6_000);                       // el timeout de lectura de las pruebas es de 2 s
        UUID batch = idOf(registerBatch(10));
        byte[] bytes = jpeg();

        long started = System.nanoTime();
        HttpResponse<String> response = attach(batch, null, FilePart.files("a.jpg", "image/jpeg", bytes), FilePart.files("b.jpg", "image/jpeg", png()));
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.statusCode()).as("201, no un error").isEqualTo(201);
        assertThat(took).as("un solo timeout: el resto va directo a la cola").isLessThan(Duration.ofSeconds(5));
        JsonNode created = items(response);
        assertThat(created).hasSize(2);
        for (JsonNode item : created) {
            assertThat(item.path("status").asString()).isEqualTo("PENDING");
            assertThat(item.path("url").isNull()).isTrue();
            assertThat(item.path("uploadedAt").isNull()).isTrue();
            assertThat(item.propertyNames()).as("sin intentos ni errores de operación").doesNotContain("attempts", "lastError", "nextAttemptAt");
        }
        assertThat(uploadRequests()).as("tras el primer fallo no se vuelve a intentar en la petición").hasSize(1);
        UUID first = idOfItem(created.get(0));
        assertThat(jdbc.sql("SELECT content FROM evidence_upload_queue WHERE evidence_id = :id").param("id", first).query(byte[].class).single()).isEqualTo(bytes);
        assertThat(queued(idOfItem(created.get(1)))).isEqualTo(1);
        assertThat((String) column(first, "last_error")).isNotBlank();
        assertThat(items(getHttp(batchEvidence(batch), OPERATOR_OF_A)).get(0).path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void serverErrorsRateLimitsAndBadCredentialsAlsoLeaveItPending() throws Exception {
        UUID batch = idOf(registerBatch(10));
        for (int status : new int[]{500, 502, 503, 429, 420, 408, 401, 403}) {
            clearStubs();
            circuitBreakers.circuitBreaker("cloudinary").reset();
            stubUploadStatus(status, "{\"error\":{\"message\":\"fallo " + status + "\"}}");

            HttpResponse<String> response = attach(batch, null, photo());

            assertThat(response.statusCode()).as("HTTP " + status + " de Cloudinary").isEqualTo(201);
            assertThat(items(response).get(0).path("status").asString()).as("HTTP " + status).isEqualTo("PENDING");
        }
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isEqualTo(8);
    }

    @Test
    void aResponseThatIsNotUnderstoodIsNotSuccessAndNeverInventsAUrl() throws Exception {
        UUID batch = idOf(registerBatch(10));
        for (String body : List.of("{}", "{\"secure_url\":\"http://inseguro/x.jpg\"}", "{\"secure_url\":42}", "no es json")) {
            clearStubs();
            circuitBreakers.circuitBreaker("cloudinary").reset();
            stubUploadStatus(200, body);

            HttpResponse<String> response = attach(batch, null, photo());

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(items(response).get(0).path("status").asString()).as(body).isEqualTo("PENDING");
            assertThat(items(response).get(0).path("url").isNull()).isTrue();
        }
    }

    @Test
    void ifCloudinaryRejectsTheImageTheRequestIsA400AndNothingIsStored() throws Exception {
        stubUploadStatus(400, "{\"error\":{\"message\":\"Invalid image file\"}}");
        UUID batch = idOf(registerBatch(10));

        HttpResponse<String> response = attach(batch, null, photo());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).path("code").asString()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(body(response).path("message").asString()).contains("JPEG, PNG y WebP");
        assertThat(count("SELECT count(*) FROM evidence")).isZero();
        assertThat(circuitBreakers.circuitBreaker("cloudinary").getMetrics().getNumberOfFailedCalls()).as("un archivo malo no es una falla del servicio").isZero();
    }

    // ---- borrar también pasa por el circuito (M5) ----

    @Autowired
    com.nodotextil.trazatex.production.application.port.out.EvidenceStoragePort storage;

    @Test
    void deletingAnImageGoesThroughTheCircuitBreakerAndStopsReachingCloudinaryOnceItIsOpen() {
        var destroy = com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/v1_1/.*/image/destroy");
        CLOUDINARY.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(destroy).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(503).withBody("{}")));
        String publicId = UUID.randomUUID().toString();

        for (int i = 0; i < 2; i++) {                      // minimum-calls = 2, 100 % de fallos → abre
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.delete(publicId))
                    .isInstanceOf(com.nodotextil.trazatex.production.application.exception.StorageTransientException.class);
        }
        assertThat(circuitBreakers.circuitBreaker("cloudinary").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = CLOUDINARY.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(destroy)).size();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.delete(publicId))
                .isInstanceOf(com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException.class);
        assertThat(CLOUDINARY.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(destroy)).size())
                .as("con el circuito abierto no se llama a Cloudinary").isEqualTo(before);
    }

    // ---- el circuito ----

    @Test
    void theCircuitOpensAfterRepeatedFailuresThenRequestsStopReachingCloudinaryAndTheyQueueInstantly() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        attach(batch, null, photo());
        attach(batch, null, photo());          // minimum-calls = 2, 100 % de fallos → abre
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("cloudinary");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = uploadRequests().size();

        long started = System.nanoTime();
        HttpResponse<String> response = attach(batch, null, photo());
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(items(response).get(0).path("status").asString()).isEqualTo("PENDING");
        assertThat(uploadRequests()).as("con el circuito abierto no se llama a Cloudinary").hasSize(callsBefore);
        assertThat(took).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void afterTheWaitTheCircuitProbesAndClosesWhenCloudinaryIsBack() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        attach(batch, null, photo());
        attach(batch, null, photo());
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("cloudinary");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        clearStubs();
        stubUploadOk();
        Thread.sleep(3_200);        // wait-in-open-state de las pruebas: 3 s; la siguiente llamada pasa a HALF_OPEN y prueba

        HttpResponse<String> probe1 = attach(batch, null, photo());
        HttpResponse<String> probe2 = attach(batch, null, photo());

        assertThat(items(probe1).get(0).path("status").asString()).isEqualTo("UPLOADED");
        assertThat(items(probe2).get(0).path("status").asString()).isEqualTo("UPLOADED");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---- el job ----

    @Test
    void theJobUploadsWhatWasPendingOnceCloudinaryIsBackAndFreesTheBytes() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        JsonNode created = items(attach(batch, null, photo(), FilePart.files("b.png", "image/png", png())));
        UUID first = idOfItem(created.get(0));
        UUID second = idOfItem(created.get(1));
        assertThat(queued(first)).isEqualTo(1);
        clearStubs();
        stubUploadOk();
        circuitBreakers.circuitBreaker("cloudinary").reset();

        assertThat(pendingUploads.uploadDue()).as("todavía no es la hora del primer reintento").isZero();
        clock.advance(Duration.ofSeconds(31));
        assertThat(pendingUploads.uploadDue()).isEqualTo(2);

        for (UUID id : List.of(first, second)) {
            assertThat(column(id, "status")).isEqualTo("UPLOADED");
            assertThat((String) column(id, "url")).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/" + id + ".jpg");
            assertThat(timestamp(id, "uploaded_at")).isNotNull();
            assertThat(queued(id)).isZero();
            assertThat(column(id, "next_attempt_at")).isNull();
        }
        List<String> publicIds = uploadRequests().stream().map(r -> part(r, "public_id")).toList();
        assertThat(publicIds).containsSubsequence(first.toString(), second.toString());
        JsonNode listed = items(getHttp(batchEvidence(batch), OPERATOR_OF_A));
        assertThat(listed).extracting(i -> i.path("status").asString()).containsOnly("UPLOADED");
        assertThat(pendingUploads.uploadDue()).as("ya no queda nada").isZero();
    }

    @Test
    void theWaitBetweenAttemptsGrowsAndNothingIsTriedBeforeItsTime() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));
        circuitBreakers.circuitBreaker("cloudinary").reset();
        // intento 1 → +30 s, intento 2 → +60 s
        long[] expectedWaits = {30, 60};
        Object previous = timestamp(id, "next_attempt_at");
        for (int attempt = 1; attempt <= 2; attempt++) {
            clock.advance(Duration.ofMinutes(10));
            int before = uploadRequests().size();
            circuitBreakers.circuitBreaker("cloudinary").reset();

            assertThat(pendingUploads.uploadDue()).isEqualTo(1);

            assertThat(uploadRequests()).hasSize(before + 1);
            assertThat(column(id, "attempts")).isEqualTo(attempt);
            OffsetDateTime next = timestamp(id, "next_attempt_at");
            assertThat(Duration.between(clock.instant(), next.toInstant())).isEqualTo(Duration.ofSeconds(expectedWaits[attempt - 1]));
            assertThat(pendingUploads.uploadDue()).as("antes de su hora no se toca").isZero();
            assertThat(uploadRequests()).hasSize(before + 1);
        }
        assertThat(previous).isNotNull();
    }

    @Test
    void afterTheMaximumAttemptsItFailsKeepingItsBytesAndAManualRequeueUploadsIt() throws Exception {
        stubUploadStatus(500, "{}");
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));           // max-attempts de las pruebas = 3
        for (int attempt = 1; attempt <= 3; attempt++) {
            clock.advance(Duration.ofHours(1));
            circuitBreakers.circuitBreaker("cloudinary").reset();
            assertThat(pendingUploads.uploadDue()).isEqualTo(1);
        }

        assertThat(column(id, "status")).isEqualTo("FAILED");
        assertThat(column(id, "attempts")).isEqualTo(3);
        assertThat(column(id, "next_attempt_at")).isNull();
        assertThat((String) column(id, "last_error")).contains("500");
        assertThat(queued(id)).as("los bytes se conservan").isEqualTo(1);
        assertThat(items(getHttp(batchEvidence(batch), OPERATOR_OF_A)).get(0).path("status").asString()).isEqualTo("FAILED");
        clock.advance(Duration.ofDays(1));
        assertThat(pendingUploads.uploadDue()).as("una FAILED no se vuelve a intentar sola").isZero();

        // El reencolado manual documentado en docs/progress.md
        clearStubs();
        stubUploadOk();
        jdbc.sql("UPDATE evidence SET status = 'PENDING', attempts = 0, next_attempt_at = :requeuedAt, last_error = NULL WHERE id = :id AND status = 'FAILED'")
                .param("requeuedAt", clock.instant().atOffset(java.time.ZoneOffset.UTC))
                .param("id", id).update();
        clock.advance(Duration.ofDays(1));
        assertThat(pendingUploads.uploadDue()).isEqualTo(1);
        assertThat(column(id, "status")).isEqualTo("UPLOADED");
        assertThat(queued(id)).isZero();
    }

    @Test
    void anImageCloudinaryRejectsLaterIsFailedAtOnceWithoutRetries() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));
        clearStubs();
        stubUploadStatus(422, "{\"error\":{\"message\":\"Invalid image file\"}}");
        circuitBreakers.circuitBreaker("cloudinary").reset();
        clock.advance(Duration.ofMinutes(1));

        pendingUploads.uploadDue();

        assertThat(column(id, "status")).isEqualTo("FAILED");
        assertThat(column(id, "attempts")).isEqualTo(1);
        assertThat((String) column(id, "last_error")).contains("Invalid image file");
        assertThat(queued(id)).isEqualTo(1);
    }

    @Test
    void anOpenCircuitDoesNotConsumeAttempts() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));
        attach(batch, null, photo());                       // abre el circuito
        assertThat(circuitBreakers.circuitBreaker("cloudinary").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = uploadRequests().size();

        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(10));
            pendingUploads.uploadDue();
        }

        assertThat(uploadRequests()).as("no se intentó").hasSize(callsBefore);
        assertThat(column(id, "attempts")).isEqualTo(0);
        assertThat(column(id, "status")).isEqualTo("PENDING");
    }

    // ---- sin duplicar ----

    @Test
    void aLostResponseIsRetriedWithTheSamePublicIdAndNeverOverwritesOrDuplicates() throws Exception {
        // Cloudinary SÍ guardó la imagen, pero la respuesta tardó más que el timeout: el cliente la da por perdida
        stubUploadDelayed(6_000);
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));
        assertThat(column(id, "status")).isEqualTo("PENDING");
        clearStubs();
        // En el reintento Cloudinary (overwrite=false) devuelve la imagen que ya tenía
        stubUploadStatus(200, "{\"public_id\":\"" + id + "\",\"existing\":true,\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/" + id + ".jpg\"}");
        circuitBreakers.circuitBreaker("cloudinary").reset();
        clock.advance(Duration.ofMinutes(1));

        pendingUploads.uploadDue();

        List<com.github.tomakehurst.wiremock.verification.LoggedRequest> sent = uploadRequests();
        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(r -> part(r, "public_id")).containsOnly(id.toString());
        assertThat(sent).extracting(r -> part(r, "overwrite")).containsOnly("false");
        assertThat(column(id, "status")).isEqualTo("UPLOADED");
        assertThat(count("SELECT count(*) FROM evidence WHERE owner_id = ?", batch)).as("una sola evidencia").isEqualTo(1);
    }

    @Test
    void twoJobsRunningAtTheSameTimeUploadAnEvidenceOnlyOnce() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        UUID id = idOfItem(items(attach(batch, null, photo())).get(0));
        clearStubs();
        stubUploadDelayed(1_000);                           // la subida tarda: el segundo job llega mientras el primero sube
        circuitBreakers.circuitBreaker("cloudinary").reset();
        clock.advance(Duration.ofMinutes(1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> pendingUploads.uploadDue()));
                Thread.sleep(150);
            }
            int processed = 0;
            for (Future<Integer> run : runs) {
                processed += run.get(30, TimeUnit.SECONDS);
            }

            assertThat(processed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(uploadRequests().stream().filter(r -> part(r, "public_id").equals(id.toString())).count()).as("1 intento fallido + 1 subida").isEqualTo(2);
        assertThat(column(id, "status")).isEqualTo("UPLOADED");
    }

    // ---- una subida lenta no retiene el lote ni la transformación ----

    private void assertNobodyHoldsTheRowLocks(String table, UUID id) {
        // si la subida tuviera el lote/transformación bloqueado, FOR UPDATE NOWAIT fallaría
        inTx(() -> jdbc.sql("SELECT id FROM " + table + " WHERE id = :id FOR UPDATE NOWAIT").param("id", id).query(UUID.class).single());
    }

    private void awaitCloudinaryReceivedTheUpload() {
        await().atMost(Duration.ofSeconds(10)).until(() -> uploadRequests().size() >= 1);
    }

    @Test
    void aSlowUploadDoesNotMakeASplitWait() throws Exception {
        for (String key : new String[]{null, UUID.randomUUID().toString()}) {          // sin y con Idempotency-Key (el filtro abre una transacción)
            CLOUDINARY.resetAll();
            stubUploadDelayed(1_500);
            UUID batch = idOf(registerBatch(10));
            CompletableFuture<HttpResponse<String>> upload = CompletableFuture.supplyAsync(() -> attach(batch, key, photo()));
            awaitCloudinaryReceivedTheUpload();
            assertNobodyHoldsTheRowLocks("batches", batch);

            long started = System.nanoTime();
            postAs(OPERATOR_OF_A, "/api/batches/" + batch + "/split", obj("children", List.of(obj("quantityKg", 4), obj("quantityKg", 6))))
                    .andExpect(status().isCreated());
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertThat(upload.isDone()).as("la subida seguía en curso cuando terminó la división (clave=" + key + ")").isFalse();
            assertThat(took).as("la división no esperó a Cloudinary").isLessThan(Duration.ofMillis(1_000));
            HttpResponse<String> uploaded = upload.get(20, TimeUnit.SECONDS);
            assertThat(uploaded.statusCode()).isEqualTo(201);
            assertThat(items(uploaded).get(0).path("status").asString()).isEqualTo("UPLOADED");
        }
    }

    @Test
    void aSlowUploadDoesNotMakeATransferWait() throws Exception {
        stubUploadDelayed(1_500);
        UUID batch = idOf(registerBatch(10));
        String key = UUID.randomUUID().toString();
        CompletableFuture<HttpResponse<String>> upload = CompletableFuture.supplyAsync(() -> attach(batch, key, photo()));
        awaitCloudinaryReceivedTheUpload();

        long started = System.nanoTime();
        postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(batch))).andExpect(status().isCreated());
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(upload.isDone()).isFalse();
        assertThat(took).isLessThan(Duration.ofMillis(1_000));
        assertThat(upload.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
    }

    @Test
    void aSlowUploadToATransformationDoesNotMakeItsCompletionWait() throws Exception {
        stubUploadDelayed(1_500);
        UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-E2")), idOf(registerBatch(10))));
        String key = UUID.randomUUID().toString();
        CompletableFuture<HttpResponse<String>> upload = CompletableFuture.supplyAsync(
                () -> postMultipart(transformationEvidence(transformation), OPERATOR_OF_A, newBoundary(), key, photo()));
        awaitCloudinaryReceivedTheUpload();
        assertNobodyHoldsTheRowLocks("transformations", transformation);

        long started = System.nanoTime();
        postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 9)),
                "wasteKg", 0.5, "wasteReason", "Orillo")).andExpect(status().isOk());
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(upload.isDone()).isFalse();
        assertThat(took).isLessThan(Duration.ofMillis(1_000));
        assertThat(upload.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
    }
}
