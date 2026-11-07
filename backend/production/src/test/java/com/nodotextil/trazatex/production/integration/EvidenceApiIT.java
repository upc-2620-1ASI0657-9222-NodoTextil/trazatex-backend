package com.nodotextil.trazatex.production.integration;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nodotextil.trazatex.production.application.port.in.UploadPendingEvidenceUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.SampleImages.jpeg;
import static com.nodotextil.trazatex.production.domain.SampleImages.jpegOfSize;
import static com.nodotextil.trazatex.production.domain.SampleImages.png;
import static com.nodotextil.trazatex.production.domain.SampleImages.webp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US42: adjuntar y listar fotos de un lote o una transformación; validación, permisos y privacidad entre empresas. */
class EvidenceApiIT extends EvidenceItBase {

    @Autowired
    UploadPendingEvidenceUseCase pendingUploads;

    private JsonNode items(HttpResponse<String> response) {
        return body(response).path("items");
    }

    // ---- escenarios 1 y 2 de US42: en lote y en transformación ----

    @Test
    void photosAttachedToABatchAreUploadedToCloudinaryAndListed() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        byte[] first = jpeg();
        byte[] second = png();

        HttpResponse<String> response = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("frente.jpg", "image/jpeg", first), FilePart.files("lateral.png", "image/png", second));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode created = items(response);
        assertThat(created).hasSize(2);
        for (JsonNode item : created) {
            assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "ownerType", "ownerId", "status", "url", "contentType",
                    "sizeBytes", "originalFilename", "uploadedBy", "createdAt", "uploadedAt");
            assertThat(item.path("status").asString()).isEqualTo("UPLOADED");
            assertThat(item.path("ownerType").asString()).isEqualTo("BATCH");
            assertThat(item.path("ownerId").asString()).isEqualTo(batch.toString());
            assertThat(item.path("url").asString()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/" + item.path("id").asString() + ".jpg");
            assertThat(item.path("uploadedBy").asString()).isEqualTo(OPERATOR_A.toString());
            assertThat(item.path("uploadedAt").isNull()).isFalse();
        }
        assertThat(created.get(0).path("originalFilename").asString()).isEqualTo("frente.jpg");
        assertThat(created.get(1).path("contentType").asString()).isEqualTo("image/png");
        assertThat(created.get(1).path("sizeBytes").asLong()).isEqualTo(second.length);

        // lo que viajó a Cloudinary: public_id = id de la evidencia, sin sobrescribir, firmado, y el secreto no viaja
        List<LoggedRequest> sent = uploadRequests();
        assertThat(sent).hasSize(2);
        for (int i = 0; i < 2; i++) {
            LoggedRequest request = sent.get(i);
            String id = created.get(i).path("id").asString();
            assertThat(part(request, "public_id")).isEqualTo(id);
            assertThat(part(request, "overwrite")).isEqualTo("false");
            assertThat(part(request, "allowed_formats")).isEqualTo("jpg,png,webp");
            assertThat(part(request, "api_key")).isEqualTo(API_KEY);
            assertThat(part(request, "signature")).isEqualTo(expectedSignature(id, part(request, "timestamp")));
            assertThat(new String(request.getBody(), StandardCharsets.ISO_8859_1)).doesNotContain(API_SECRET);
            assertThat(request.getHeaders().toString()).doesNotContain(API_SECRET);
        }
        assertThat(partBytes(sent.get(0), "file")).isEqualTo(first);
        assertThat(partBytes(sent.get(1), "file")).isEqualTo(second);

        JsonNode listed = items(getHttp(batchEvidence(batch), OPERATOR_OF_A));
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).path("id").asString()).isEqualTo(created.get(0).path("id").asString());
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).as("lo ya subido no deja bytes en la cola").isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox")).as("las evidencias no publican eventos").isEqualTo(1);   // solo el BatchRegistered
    }

    @Test
    void photosAttachedToATransformationAreKeptAndListedThere() throws Exception {
        stubUploadOk();
        UUID transformation = idOf(startTransformation("SPINNING", idOf(registerMachine("MQ-E1")), idOf(registerBatch(10))));

        HttpResponse<String> response = postMultipart(transformationEvidence(transformation), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("proceso.webp", "image/webp", webp()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(items(response).get(0).path("ownerType").asString()).isEqualTo("TRANSFORMATION");
        assertThat(items(getHttp(transformationEvidence(transformation), ADMIN_OF_A))).hasSize(1);
        // y también sobre una transformación ya finalizada
        postAs(OPERATOR_OF_A, "/api/transformations/" + transformation + "/complete", obj("outputs", List.of(obj("materialType", "Hilo", "quantityKg", 9)),
                "wasteKg", 0.5, "wasteReason", "Orillo")).andExpect(status().isOk());
        assertThat(postMultipart(transformationEvidence(transformation), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("final.jpg", "image/jpeg", jpeg())).statusCode()).isEqualTo(201);
        assertThat(items(getHttp(transformationEvidence(transformation), OPERATOR_OF_A))).hasSize(2);
    }

    // ---- escenario 3: formato no soportado (por los primeros bytes) ----

    @Test
    void theFormatComesFromTheFirstBytesNotFromTheExtensionNorTheDeclaredType() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));

        HttpResponse<String> fake = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("foto.jpg", "image/jpeg", "esto es un texto, no una imagen".getBytes(StandardCharsets.UTF_8)));
        HttpResponse<String> real = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("sin-extension.txt", "text/plain", png()));

        assertThat(fake.statusCode()).isEqualTo(400);
        JsonNode error = body(fake);
        assertThat(error.path("code").asString()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(error.path("message").asString()).contains("JPEG, PNG y WebP");
        assertThat(error.path("details")).extracting(JsonNode::asString).containsExactly("foto.jpg");
        assertThat(error.propertyNames()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(real.statusCode()).as("un PNG con extensión .txt y tipo text/plain es válido").isEqualTo(201);
        assertThat(items(real).get(0).path("contentType").asString()).isEqualTo("image/png");
    }

    @Test
    void oneInvalidFileRejectsTheWholeRequestAndNothingIsUploadedNorStored() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));

        HttpResponse<String> response = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null,
                FilePart.files("ok.jpg", "image/jpeg", jpeg()), FilePart.files("mala.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.UTF_8)),
                FilePart.files("ok2.webp", "image/webp", webp()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(uploadRequests()).isEmpty();
        assertThat(count("SELECT count(*) FROM evidence")).isZero();
        assertThat(items(getHttp(batchEvidence(batch), OPERATOR_OF_A))).isEmpty();
    }

    @Test
    void jpegPngAndWebpAreAcceptedAndEveryOtherFormatIsRejected() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("a.jpg", "image/jpeg", jpeg()),
                FilePart.files("b.png", "image/png", png()), FilePart.files("c.webp", "image/webp", webp())).statusCode()).isEqualTo(201);

        for (byte[] other : List.of("<svg/>".getBytes(StandardCharsets.UTF_8), "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x42, 0x4D, 0, 0, 0, 0}, "GIF89a".getBytes(StandardCharsets.UTF_8))) {
            assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("x.jpg", "image/jpeg", other)).statusCode())
                    .isEqualTo(400);
        }
    }

    // ---- cuántos archivos ----

    @Test
    void upToFiveFilesPerRequestAreAcceptedAndMoreAreRejected() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        List<FilePart> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(FilePart.files("f" + i + ".jpg", "image/jpeg", jpeg()));
        }
        List<FilePart> six = new ArrayList<>(five);
        six.add(FilePart.files("f5.jpg", "image/jpeg", jpeg()));

        HttpResponse<String> tooMany = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, six);
        HttpResponse<String> allowed = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, five);

        assertThat(tooMany.statusCode()).isEqualTo(400);
        assertThat(body(tooMany).path("code").asString()).isEqualTo("TOO_MANY_FILES");
        assertThat(allowed.statusCode()).isEqualTo(201);
        assertThat(items(allowed)).hasSize(5);
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(5);
    }

    @Test
    void aRequestWithoutFilesOrWithEmptyFilesOrNotMultipartIsRejected() throws Exception {
        UUID batch = idOf(registerBatch(10));

        HttpResponse<String> noParts = postRaw(batchEvidence(batch), OPERATOR_OF_A, "multipart/form-data; boundary=X", "--X--\r\n".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> otherField = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null,
                new FilePart("fotos", "a.jpg", "image/jpeg", jpeg()));
        HttpResponse<String> empty = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("vacio.jpg", "image/jpeg", new byte[0]));
        HttpResponse<String> json = postRaw(batchEvidence(batch), OPERATOR_OF_A, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(noParts.statusCode()).isEqualTo(400);
        assertThat(body(noParts).path("code").asString()).isEqualTo("NO_FILES");
        assertThat(otherField.statusCode()).isEqualTo(400);
        assertThat(body(otherField).path("code").asString()).isEqualTo("NO_FILES");
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(body(empty).path("code").asString()).isEqualTo("NO_FILES");
        assertThat(json.statusCode()).isEqualTo(415);
        assertThat(body(json).path("code").asString()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    // ---- tamaño: 10 MB por archivo, con un ApiError y no con un error genérico ----

    @Test
    void aFileOfExactlyTenMegabytesIsAcceptedAndOneByteMoreIsRejectedWithAnApiError413() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        int limit = 10 * 1024 * 1024;

        HttpResponse<String> exact = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("diez.jpg", "image/jpeg", jpegOfSize(limit)));
        HttpResponse<String> over = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, FilePart.files("once.jpg", "image/jpeg", jpegOfSize(limit + 1)));

        assertThat(exact.statusCode()).isEqualTo(201);
        assertThat(partBytes(uploadRequests().get(0), "file")).hasSize(limit);
        assertThat(over.statusCode()).isEqualTo(413);
        assertThat(over.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        JsonNode error = body(over);
        assertThat(error.propertyNames()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(error.path("code").asString()).isEqualTo("FILE_TOO_LARGE");
        assertThat(error.path("message").asString()).contains("10 MB");
        assertThat(uploadRequests()).as("el archivo grande no se subió").hasSize(1);
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(1);
    }

    /**
     * Un archivo muy por encima del límite (40 MB): Tomcat deja de leer al pasar los 10 MB y debe poder descartar el resto del cuerpo
     * (server.tomcat.max-swallow-size) para que el cliente reciba el ApiError y no un corte de conexión.
     */
    @Test
    void aFarLargerFileStillGetsTheApiErrorAndNotAConnectionReset() throws Exception {
        UUID batch = idOf(registerBatch(10));

        for (String key : new String[]{null, UUID.randomUUID().toString()}) {
            HttpResponse<String> over = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key,
                    FilePart.files("enorme.jpg", "image/jpeg", jpegOfSize(40 * 1024 * 1024)));

            assertThat(over.statusCode()).isEqualTo(413);
            assertThat(body(over).path("code").asString()).isEqualTo("FILE_TOO_LARGE");
        }
    }

    @Test
    void theTooLargeErrorIsTheSameApiErrorWithAnIdempotencyKeyAndItLeavesTheKeyFree() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        String key = UUID.randomUUID().toString();
        int limit = 10 * 1024 * 1024;

        HttpResponse<String> over = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, FilePart.files("once.jpg", "image/jpeg", jpegOfSize(limit + 1)));

        assertThat(over.statusCode()).isEqualTo(413);
        assertThat(over.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(body(over).path("code").asString()).isEqualTo("FILE_TOO_LARGE");
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, FilePart.files("bien.jpg", "image/jpeg", jpeg())).statusCode())
                .as("la misma clave sirve después de corregir el archivo").isEqualTo(201);
    }

    // ---- permisos ----

    @Test
    void onlyAnOperatorOfTheOwningCompanyAttachesAndBothCompanyRolesList() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());

        assertThat(postMultipart(batchEvidence(batch), ADMIN_OF_A, newBoundary(), null, photo).statusCode()).as("COMPANY_ADMIN no adjunta").isEqualTo(403);
        assertThat(postMultipart(batchEvidence(batch), LICENSE_OWNER, newBoundary(), null, photo).statusCode()).isEqualTo(403);
        assertThat(postMultipart(batchEvidence(batch), null, newBoundary(), null, photo).statusCode()).as("sin identidad").isEqualTo(401);
        HttpResponse<String> foreign = postMultipart(batchEvidence(batch), OPERATOR_OF_B, newBoundary(), null, photo);
        assertThat(foreign.statusCode()).as("otra empresa: el lote no existe").isEqualTo(404);
        assertThat(body(foreign).path("code").asString()).isEqualTo("BATCH_NOT_FOUND");
        assertThat(postMultipart(batchEvidence(UUID.randomUUID()), OPERATOR_OF_A, newBoundary(), null, photo).statusCode()).isEqualTo(404);
        assertThat(postMultipart(transformationEvidence(UUID.randomUUID()), OPERATOR_OF_A, newBoundary(), null, photo).statusCode()).isEqualTo(404);
        assertThat(uploadRequests()).isEmpty();

        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, photo).statusCode()).isEqualTo(201);
        assertThat(getHttp(batchEvidence(batch), OPERATOR_OF_A).statusCode()).isEqualTo(200);
        assertThat(getHttp(batchEvidence(batch), ADMIN_OF_A).statusCode()).isEqualTo(200);
        assertThat(getHttp(batchEvidence(batch), LICENSE_OWNER).statusCode()).isEqualTo(403);
        assertThat(getHttp(batchEvidence(batch), OPERATOR_OF_B).statusCode()).as("otra empresa no lista").isEqualTo(404);
        assertThat(getHttp(batchEvidence(batch), null).statusCode()).isEqualTo(401);
    }

    @Test
    void anInactiveOrUnknownCompanyCanStillAttachEvidence() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());

        jdbc.sql("UPDATE company_projection SET status = 'INACTIVE' WHERE company_id = :c").param("c", COMPANY_A).update();
        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, photo).statusCode()).isEqualTo(201);
        jdbc.sql("DELETE FROM company_projection WHERE company_id = :c").param("c", COMPANY_A).update();
        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, photo).statusCode()).isEqualTo(201);
    }

    @Test
    void eachCompanySeesOnlyTheEvidenceItUploadedEvenAfterATransfer() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());
        postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), null, photo);
        UUID transfer = idOf(read(postAs(OPERATOR_OF_A, "/api/transfers", obj("targetCompanyId", COMPANY_B, "batchIds", List.of(batch)))
                .andExpect(status().isCreated())));
        postAs(ADMIN_OF_B, "/api/transfers/" + transfer + "/accept").andExpect(status().isOk());

        assertThat(items(getHttp(batchEvidence(batch), OPERATOR_OF_B))).as("la nueva dueña no ve las fotos de la anterior").isEmpty();
        assertThat(getHttp(batchEvidence(batch), OPERATOR_OF_A).statusCode()).as("la anterior ya no ve el lote").isEqualTo(404);
        assertThat(postMultipart(batchEvidence(batch), OPERATOR_OF_B, newBoundary(), null, FilePart.files("b.jpg", "image/jpeg", jpeg())).statusCode()).isEqualTo(201);
        JsonNode own = items(getHttp(batchEvidence(batch), ADMIN_OF_B));
        assertThat(own).hasSize(1);
        assertThat(own.get(0).path("originalFilename").asString()).isEqualTo("b.jpg");
        assertThat(count("SELECT count(*) FROM evidence WHERE owner_id = ?", batch)).isEqualTo(2);
    }

    // ---- Idempotency-Key con multipart ----

    @Test
    void aRetryWithTheSameKeyAndFilesButAnotherBoundaryReturnsTheSameResponseAndStoresNothingNew() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        String key = UUID.randomUUID().toString();
        FilePart[] files = {FilePart.files("a.jpg", "image/jpeg", jpeg()), FilePart.files("b.png", "image/png", png())};

        HttpResponse<String> first = postMultipart(batchEvidence(batch), OPERATOR_OF_A, "----PrimerIntento1234567890", key, files);
        HttpResponse<String> retry = postMultipart(batchEvidence(batch), OPERATOR_OF_A, "----Reintento-con-otro-separador-9876543210", key, files);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(retry.statusCode()).as("un reintento legítimo no es un 422").isEqualTo(201);
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(count("SELECT count(*) FROM evidence")).as("no se duplicó nada").isEqualTo(2);
        assertThat(uploadRequests()).as("ni se volvió a subir").hasSize(2);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'")).isEqualTo(1);
    }

    @Test
    void theSameKeyWithOtherFilesIs422() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        String key = UUID.randomUUID().toString();
        postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, FilePart.files("a.jpg", "image/jpeg", jpeg()));

        HttpResponse<String> otherBytes = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, FilePart.files("a.jpg", "image/jpeg", png()));
        HttpResponse<String> otherName = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, FilePart.files("otra.jpg", "image/jpeg", jpeg()));
        HttpResponse<String> otherBatch = postMultipart(batchEvidence(idOf(registerBatch(5))), OPERATOR_OF_A, newBoundary(), key, FilePart.files("a.jpg", "image/jpeg", jpeg()));

        for (HttpResponse<String> response : List.of(otherBytes, otherName, otherBatch)) {
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(body(response).path("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(1);
    }

    @Test
    void theIdempotentReplayKeepsTheStatusItHadAndNeverUploadsAgain() throws Exception {
        stubUploadStatus(503, "{\"error\":{\"message\":\"Service Unavailable\"}}");
        UUID batch = idOf(registerBatch(10));
        String key = UUID.randomUUID().toString();
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());

        HttpResponse<String> first = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, photo);
        clearStubs();
        stubUploadOk();
        HttpResponse<String> retry = postMultipart(batchEvidence(batch), OPERATOR_OF_A, newBoundary(), key, photo);

        assertThat(items(first).get(0).path("status").asString()).isEqualTo("PENDING");
        assertThat(body(retry)).as("la respuesta guardada, con el estado de entonces").isEqualTo(body(first));
        assertThat(uploadRequests()).as("solo el primer intento (el que falló) llegó a Cloudinary; el reintento no").hasSize(1);
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(1);
    }

    // ---- sin Cloudinary: la cola sobrevive a un reinicio ----

    @Test
    void evidencePendingFromBeforeIsUploadedByTheJobOnceCloudinaryIsConfigured() throws Exception {
        stubUploadOk();
        UUID batch = idOf(registerBatch(10));
        UUID id = UUID.randomUUID();
        // lo que dejó una ejecución anterior sin CLOUDINARY_URL: la fila PENDING y sus bytes en la cola
        jdbc.sql("""
                INSERT INTO evidence (id, owner_type, owner_id, company_id, status, original_filename, content_type, size_bytes, uploaded_by,
                                      created_at, attempts, next_attempt_at, last_error)
                VALUES (:id, 'BATCH', :batch, :company, 'PENDING', 'vieja.jpg', 'image/jpeg', :size, :user, :at, 0, :at, 'Cloudinary no está configurado')
                """).param("id", id).param("batch", batch).param("company", COMPANY_A).param("size", jpeg().length).param("user", OPERATOR_A)
                .param("at", java.time.OffsetDateTime.ofInstant(START, java.time.ZoneOffset.UTC)).update();
        jdbc.sql("INSERT INTO evidence_upload_queue (evidence_id, content) VALUES (:id, :content)").param("id", id).param("content", jpeg()).update();
        clock.advance(Duration.ofMinutes(1));

        assertThat(pendingUploads.uploadDue()).isEqualTo(1);

        JsonNode listed = items(getHttp(batchEvidence(batch), OPERATOR_OF_A));
        assertThat(listed.get(0).path("status").asString()).isEqualTo("UPLOADED");
        assertThat(listed.get(0).path("url").asString()).endsWith(id + ".jpg");
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).isZero();
        assertThat(partBytes(uploadRequests().get(0), "file")).isEqualTo(jpeg());
    }
}
