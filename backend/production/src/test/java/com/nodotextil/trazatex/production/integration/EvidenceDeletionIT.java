package com.nodotextil.trazatex.production.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.port.in.DeleteEvidenceUseCase;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nodotextil.trazatex.production.domain.SampleImages.jpeg;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5: borrar una evidencia deja primero la marca (la evidencia desaparece de los listados) y luego un job borra la imagen en Cloudinary y las
 * referencias. La llamada a Cloudinary no retiene ninguna transacción de la base: mientras tarda, no hay conexiones «idle in transaction».
 */
class EvidenceDeletionIT extends EvidenceItBase {

    static final com.github.tomakehurst.wiremock.matching.UrlPattern DESTROY = WireMock.urlPathMatching("/v1_1/.*/image/destroy");

    @Autowired
    DeleteEvidenceUseCase deletions;

    private UUID uploadedEvidence(UUID batch) throws Exception {
        stubUploadOk();
        HttpResponse<String> response = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("a.jpg", "image/jpeg", jpeg()));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(body(response).path("items").get(0).path("id").asString());
    }

    private int attempts(UUID evidence) {
        return jdbc.sql("SELECT purge_attempts FROM evidence WHERE id = :id").param("id", evidence).query(Integer.class).single();
    }

    /** Segundos de espera que quedan según el reloj simulado de la prueba (el {@code now()} de PostgreSQL es la hora real). */
    private long waitSeconds(UUID evidence) {
        java.time.OffsetDateTime at = jdbc.sql("SELECT purge_retry_at FROM evidence WHERE id = :id").param("id", evidence)
                .query((rs, n) -> rs.getObject(1, java.time.OffsetDateTime.class)).single();
        return java.time.Duration.between(clock.instant(), at.toInstant()).toSeconds();
    }

    private long rows(UUID evidence) {
        return jdbc.sql("SELECT count(*) FROM evidence WHERE id = :id").param("id", evidence).query(Long.class).single();
    }

    @Test
    void deletingHidesTheEvidenceAtOnceAndTheImageIsRemovedLaterByTheJob() throws Exception {
        UUID batch = idOf(registerBatch(10));
        UUID evidence = uploadedEvidence(batch);
        CLOUDINARY.stubFor(WireMock.post(DESTROY).willReturn(WireMock.okJson("{\"result\":\"ok\"}")));

        deletions.delete(EvidenceOwnerType.BATCH, batch, evidence, new Actor(OPERATOR_A, COMPANY_A, Role.OPERATOR));

        assertThat(body(getHttp(batchEvidence(batch), OPERATOR_OF_A)).path("items")).as("ya no se lista").isEmpty();
        assertThat(rows(evidence)).as("la fila sigue hasta que se borre la imagen").isEqualTo(1);
        assertThat(CLOUDINARY.findAll(WireMock.postRequestedFor(DESTROY))).isEmpty();

        assertThat(deletions.purgeDeleted()).isEqualTo(1);

        assertThat(CLOUDINARY.findAll(WireMock.postRequestedFor(DESTROY))).hasSize(1);
        assertThat(rows(evidence)).isZero();
    }

    @Test
    void ifCloudinaryFailsTheMarkStaysItIsDeferredAndTheNextRunAfterTheWaitTriesAgain() throws Exception {
        UUID batch = idOf(registerBatch(10));
        UUID evidence = uploadedEvidence(batch);
        deletions.delete(EvidenceOwnerType.BATCH, batch, evidence, new Actor(OPERATOR_A, COMPANY_A, Role.OPERATOR));
        CLOUDINARY.stubFor(WireMock.post(DESTROY).willReturn(WireMock.aResponse().withStatus(503).withBody("{}")));

        assertThat(deletions.purgeDeleted()).as("no lanza: aplaza la fila y termina").isZero();
        assertThat(rows(evidence)).as("sigue marcada para borrar").isEqualTo(1);
        assertThat(attempts(evidence)).isEqualTo(1);
        assertThat(waitSeconds(evidence)).as("1.ª espera").isEqualTo(10);
        int calls = CLOUDINARY.findAll(WireMock.postRequestedFor(DESTROY)).size();
        assertThat(deletions.purgeDeleted()).isZero();
        assertThat(CLOUDINARY.findAll(WireMock.postRequestedFor(DESTROY))).as("antes de que venza la espera no se vuelve a llamar").hasSize(calls);

        CLOUDINARY.stubFor(WireMock.post(DESTROY).willReturn(WireMock.okJson("{\"result\":\"not found\"}")));
        circuitBreakers.circuitBreaker("cloudinary").reset();
        clock.advance(java.time.Duration.ofSeconds(11));
        assertThat(deletions.purgeDeleted()).isEqualTo(1);
        assertThat(rows(evidence)).isZero();
    }

    /** Una evidencia que falla siempre no frena a las demás (antes la primera excepción cortaba toda la corrida). */
    @Test
    void aFailingEvidenceDoesNotStopThePurgeOfTheOthers() throws Exception {
        UUID batch = idOf(registerBatch(10));
        UUID bad = uploadedEvidence(batch);
        UUID good = uploadedEvidence(batch);
        Actor actor = new Actor(OPERATOR_A, COMPANY_A, Role.OPERATOR);
        deletions.delete(EvidenceOwnerType.BATCH, batch, bad, actor);
        deletions.delete(EvidenceOwnerType.BATCH, batch, good, actor);
        CLOUDINARY.stubFor(WireMock.post(DESTROY).willReturn(WireMock.okJson("{\"result\":\"ok\"}")));
        CLOUDINARY.stubFor(WireMock.post(DESTROY).withRequestBody(WireMock.containing(bad.toString())).atPriority(1)
                .willReturn(WireMock.aResponse().withStatus(422).withBody("{\"error\":{\"message\":\"rechazada\"}}")));
        jdbc.sql("UPDATE evidence SET deleted_at = now() - interval '1 hour' WHERE id = :id").param("id", bad).update();   // la fallida es la más antigua: va primero

        assertThat(deletions.purgeDeleted()).as("la buena se purgó aunque la primera falló").isEqualTo(1);

        assertThat(rows(good)).isZero();
        assertThat(rows(bad)).isEqualTo(1);
        assertThat(attempts(bad)).isEqualTo(1);
        assertThat(waitSeconds(bad)).isEqualTo(10);
        jdbc.sql("DELETE FROM evidence_upload_queue WHERE evidence_id = :id").param("id", bad).update();
        jdbc.sql("DELETE FROM evidence WHERE id = :id").param("id", bad).update();
    }

    @Test
    void whileCloudinaryAnswersNoTransactionIsHeldOpen() throws Exception {
        UUID batch = idOf(registerBatch(10));
        UUID evidence = uploadedEvidence(batch);
        deletions.delete(EvidenceOwnerType.BATCH, batch, evidence, new Actor(OPERATOR_A, COMPANY_A, Role.OPERATOR));
        CLOUDINARY.stubFor(WireMock.post(DESTROY).willReturn(WireMock.okJson("{\"result\":\"ok\"}").withFixedDelay(1_200)));

        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> purge = executor.submit(() -> deletions.purgeDeleted());
            Thread.sleep(400);                                   // Cloudinary sigue respondiendo (1,2 s; el timeout de lectura de las pruebas es de 2 s)
            assertThat(purge.isDone()).as("la llamada a Cloudinary está en vuelo").isFalse();
            long idleInTransaction = jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND state LIKE 'idle in transaction%'")
                    .query(Long.class).single();
            assertThat(idleInTransaction).as("ninguna transacción abierta esperando a la red").isZero();
            assertThat(purge.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(rows(evidence)).isZero();
    }
}
