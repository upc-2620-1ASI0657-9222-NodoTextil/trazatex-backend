package com.nodotextil.trazatex.production.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.SampleImages.jpeg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** El job programado de verdad (no invocado a mano): sube lo pendiente en cuanto llega su hora, sin que nadie lo llame. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"trazatex.messaging.enabled=false",
        "trazatex.evidence.upload.enabled=true", "trazatex.evidence.upload.job-delay=PT0.2S", "trazatex.evidence.upload.job-initial-delay=PT0S"})
class EvidenceScheduledJobIT extends EvidenceItBase {

    @Test
    void theScheduledJobUploadsWhatIsPendingWhenItsTimeComes() throws Exception {
        stubUploadStatus(503, "{}");
        UUID batch = idOf(registerBatch(10));
        HttpResponse<String> created = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("a.jpg", "image/jpeg", jpeg()));
        UUID id = UUID.fromString(body(created).path("items").get(0).path("id").asString());
        assertThat(body(created).path("items").get(0).path("status").asString()).isEqualTo("PENDING");
        clearStubs();
        stubUploadOk();
        circuitBreakers.circuitBreaker("cloudinary").reset();

        Thread.sleep(800);          // el job corre varias veces, pero aún no es la hora del primer reintento
        assertThat(jdbc.sql("SELECT status FROM evidence WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("PENDING");
        clock.advance(Duration.ofMinutes(1));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT status FROM evidence WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("UPLOADED"));
        JsonNode listed = body(getHttp(batchEvidence(batch), OPERATOR_OF_A)).path("items");
        assertThat(listed.get(0).path("url").asString()).endsWith(id + ".jpg");
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isZero();
    }
}
