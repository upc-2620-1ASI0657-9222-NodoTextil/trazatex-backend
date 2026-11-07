package com.nodotextil.trazatex.quality.integration;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.domain.SampleImages.jpeg;
import static com.nodotextil.trazatex.quality.domain.SampleImages.jpegOfSize;
import static com.nodotextil.trazatex.quality.domain.SampleImages.png;
import static com.nodotextil.trazatex.quality.domain.SampleImages.webp;
import static org.assertj.core.api.Assertions.assertThat;

/** US43: adjuntar y listar fotos de un control o de una falla; validación, permisos y privacidad entre empresas. */
class EvidenceApiIT extends EvidenceItBase {

    private JsonNode items(HttpResponse<String> response) {
        return body(response).path("items");
    }

    private HttpResponse<String> attachToControl(UUID control, Caller caller, String key, FilePart... files) {
        List<FilePart> parts = new java.util.ArrayList<>(List.of(owner("controlId", control)));
        parts.addAll(List.of(files));
        return postMultipart(EVIDENCE, caller, newBoundary(), key, parts);
    }

    // ---- escenarios 1 y 2 de US43: en control y en falla ----

    @Test
    void photosAttachedToAControlAreUploadedToCloudinaryAndListed() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        byte[] first = jpeg();
        byte[] second = png();

        HttpResponse<String> response = attachToControl(control, ADMIN_OF_A, null,
                FilePart.files("frente.jpg", "image/jpeg", first), FilePart.files("lateral.png", "image/png", second));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode created = items(response);
        assertThat(created).hasSize(2);
        for (JsonNode item : created) {
            assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "ownerType", "ownerId", "status", "url", "contentType",
                    "sizeBytes", "originalFilename", "uploadedBy", "createdAt", "uploadedAt");
            assertThat(item.path("status").asString()).isEqualTo("UPLOADED");
            assertThat(item.path("ownerType").asString()).isEqualTo("CONTROL");
            assertThat(item.path("ownerId").asString()).isEqualTo(control.toString());
            assertThat(item.path("url").asString()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/" + item.path("id").asString() + ".jpg");
            assertThat(item.path("uploadedBy").asString()).isEqualTo(ADMIN_A.toString());
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

        JsonNode listed = items(getHttp(controlEvidence(control), ADMIN_OF_A));
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).path("id").asString()).isEqualTo(created.get(0).path("id").asString());
        assertThat(count("SELECT count(*) FROM evidence_upload_queue")).as("lo ya subido no deja bytes en la cola").isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT entity_type, entity_id, user_id FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'").query().singleRow())
                .containsEntry("entity_type", "QualityControl").containsEntry("entity_id", control.toString()).containsEntry("user_id", ADMIN_A);
        assertThat(count("SELECT count(*) FROM outbox")).as("las evidencias no publican eventos").isZero();
    }

    @Test
    void photosAttachedToAFailureAreKeptAndListedThere() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        UUID failure = failureOf(control);

        HttpResponse<String> response = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, owner("failureId", failure),
                FilePart.files("rotura.webp", "image/webp", webp()));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(items(response).get(0).path("ownerType").asString()).isEqualTo("FAILURE");
        assertThat(items(response).get(0).path("ownerId").asString()).isEqualTo(failure.toString());
        assertThat(items(getHttp(failureEvidence(failure), ADMIN_OF_A))).hasSize(1);
        assertThat(items(getHttp(controlEvidence(control), ADMIN_OF_A))).as("lo de la falla no se mezcla con lo del control").isEmpty();
        assertThat(jdbc.sql("SELECT entity_type FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'").query(String.class).single()).isEqualTo("QualityFailure");
    }

    @Test
    void aControlInAnyStatusAcceptsEvidence() throws Exception {
        stubUploadOk();
        UUID inProgress = inProgressControl();
        UUID finished = inProgressControl();
        addTest(finished, "CONFORMING");
        ProductionStub.answersOk();
        postAs(ADMIN_OF_A, "/api/quality/controls/" + finished + "/finish").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        ProductionStub.reset();
        UUID starting = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO quality_control (id, batch_id, company_id, control_type, status, inspector_id, start_date, cancel_reason)
                SELECT :id, gen_random_uuid(), company_id, control_type, 'CANCELLED', inspector_id, start_date, 'COMPANY_INACTIVE' FROM quality_control WHERE id = :base
                """).param("id", starting).param("base", inProgress).update();

        for (UUID control : List.of(inProgress, finished, starting)) {
            assertThat(attachToControl(control, ADMIN_OF_A, null, FilePart.files("a.jpg", "image/jpeg", jpeg())).statusCode()).isEqualTo(201);
        }
    }

    // ---- formato: por los primeros bytes ----

    @Test
    void theFormatComesFromTheFirstBytesNotFromTheExtensionNorTheDeclaredType() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();

        HttpResponse<String> ok = attachToControl(control, ADMIN_OF_A, null, FilePart.files("foto.txt", "text/plain", png()));
        HttpResponse<String> fake = attachToControl(control, ADMIN_OF_A, null, FilePart.files("falsa.jpg", "image/jpeg", "no soy una imagen".getBytes(StandardCharsets.UTF_8)));

        assertThat(ok.statusCode()).isEqualTo(201);
        assertThat(items(ok).get(0).path("contentType").asString()).isEqualTo("image/png");
        assertThat(fake.statusCode()).isEqualTo(400);
        assertThat(body(fake).path("code").asString()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(body(fake).path("message").asString()).contains("JPEG, PNG y WebP");
        assertThat(body(fake).path("details").get(0).asString()).isEqualTo("falsa.jpg");
    }

    @Test
    void oneInvalidFileRejectsTheWholeRequestAndNothingIsUploadedNorStored() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();

        HttpResponse<String> response = attachToControl(control, ADMIN_OF_A, null, FilePart.files("ok.jpg", "image/jpeg", jpeg()),
                FilePart.files("mala.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).path("code").asString()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        assertThat(uploadRequests()).isEmpty();
        assertThat(count("SELECT count(*) FROM evidence")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'")).isZero();
    }

    @Test
    void jpegPngAndWebpAreAcceptedAndEveryOtherFormatIsRejected() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        assertThat(attachToControl(control, ADMIN_OF_A, null, FilePart.files("a.jpg", "image/jpeg", jpeg()), FilePart.files("b.png", "image/png", png()),
                FilePart.files("c.webp", "image/webp", webp())).statusCode()).isEqualTo(201);

        for (byte[] other : List.of("GIF89a".getBytes(StandardCharsets.UTF_8), "%PDF-1.7".getBytes(StandardCharsets.UTF_8), "BM......".getBytes(StandardCharsets.UTF_8),
                "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8), new byte[]{0x49, 0x49, 0x2A, 0x00, 1, 2, 3, 4})) {
            HttpResponse<String> response = attachToControl(control, ADMIN_OF_A, null, FilePart.files("x.jpg", "image/jpeg", other));

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(body(response).path("code").asString()).isEqualTo("UNSUPPORTED_FILE_FORMAT");
        }
    }

    // ---- cantidad y tamaño ----

    @Test
    void upToFiveFilesPerRequestAreAcceptedAndMoreAreRejected() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        FilePart[] five = new FilePart[5];
        for (int i = 0; i < 5; i++) {
            five[i] = FilePart.files("f" + i + ".jpg", "image/jpeg", jpeg());
        }
        FilePart[] six = java.util.Arrays.copyOf(five, 6);
        six[5] = FilePart.files("f5.jpg", "image/jpeg", jpeg());

        assertThat(items(attachToControl(control, ADMIN_OF_A, null, five))).hasSize(5);
        HttpResponse<String> response = attachToControl(control, ADMIN_OF_A, null, six);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body(response).path("code").asString()).isEqualTo("TOO_MANY_FILES");
        assertThat(count("SELECT count(*) FROM evidence")).as("la petición rechazada no guardó nada").isEqualTo(5);
    }

    @Test
    void aRequestWithoutFilesOrWithEmptyFilesOrNotMultipartIsRejected() throws Exception {
        UUID control = inProgressControl();

        HttpResponse<String> none = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, owner("controlId", control));
        HttpResponse<String> empty = attachToControl(control, ADMIN_OF_A, null, FilePart.files("vacio.jpg", "image/jpeg", new byte[0]));
        HttpResponse<String> notMultipart = postRaw(EVIDENCE, ADMIN_OF_A, "application/json", ("{\"controlId\":\"" + control + "\"}").getBytes(StandardCharsets.UTF_8));

        assertThat(none.statusCode()).isEqualTo(400);
        assertThat(body(none).path("code").asString()).isEqualTo("NO_FILES");
        assertThat(empty.statusCode()).isEqualTo(400);
        assertThat(body(empty).path("code").asString()).isEqualTo("NO_FILES");
        assertThat(notMultipart.statusCode()).isEqualTo(415);
    }

    @Test
    void aFileOfExactlyTenMegabytesIsAcceptedAndOneByteMoreIsRejectedWithAnApiError413() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        int limit = 10 * 1024 * 1024;

        HttpResponse<String> exact = attachToControl(control, ADMIN_OF_A, null, FilePart.files("diez.jpg", "image/jpeg", jpegOfSize(limit)));
        HttpResponse<String> over = attachToControl(control, ADMIN_OF_A, null, FilePart.files("once.jpg", "image/jpeg", jpegOfSize(limit + 1)));

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

    /** Un archivo muy por encima del límite (40 MB): el cliente debe recibir el ApiError y no un corte de conexión (server.tomcat.max-swallow-size). */
    @Test
    void aFarLargerFileStillGetsTheApiErrorAndNotAConnectionReset() throws Exception {
        UUID control = inProgressControl();

        for (String key : new String[]{null, UUID.randomUUID().toString()}) {
            HttpResponse<String> over = attachToControl(control, ADMIN_OF_A, key, FilePart.files("enorme.jpg", "image/jpeg", jpegOfSize(40 * 1024 * 1024)));

            assertThat(over.statusCode()).isEqualTo(413);
            assertThat(body(over).path("code").asString()).isEqualTo("FILE_TOO_LARGE");
        }
    }

    @Test
    void theTooLargeErrorWithAnIdempotencyKeyLeavesTheKeyFree() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        String key = UUID.randomUUID().toString();

        HttpResponse<String> over = attachToControl(control, ADMIN_OF_A, key, FilePart.files("once.jpg", "image/jpeg", jpegOfSize(10 * 1024 * 1024 + 1)));

        assertThat(over.statusCode()).isEqualTo(413);
        assertThat(count("SELECT count(*) FROM idempotency_keys")).isZero();
        assertThat(attachToControl(control, ADMIN_OF_A, key, FilePart.files("bien.jpg", "image/jpeg", jpeg())).statusCode())
                .as("la misma clave sirve después de corregir el archivo").isEqualTo(201);
    }

    // ---- a qué se adjunta ----

    @Test
    void exactlyOneOfControlIdOrFailureIdIsRequired() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();

        HttpResponse<String> neither = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, FilePart.files("a.jpg", "image/jpeg", jpeg()));
        HttpResponse<String> both = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, owner("controlId", control), owner("failureId", UUID.randomUUID()),
                FilePart.files("a.jpg", "image/jpeg", jpeg()));
        HttpResponse<String> notAUuid = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, FilePart.field("controlId", "no-es-uuid"), FilePart.files("a.jpg", "image/jpeg", jpeg()));

        for (HttpResponse<String> response : List.of(neither, both)) {
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(body(response).path("code").asString()).isEqualTo("EVIDENCE_OWNER_REQUIRED");
        }
        assertThat(notAUuid.statusCode()).isEqualTo(400);
        assertThat(uploadRequests()).isEmpty();
    }

    // ---- permisos y privacidad ----

    @Test
    void onlyTheCompanyAdminOfTheOwningCompanyAttachesAndLists_AnOperatorGets403_US43Scenario3Literal() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());
        attachToControl(control, ADMIN_OF_A, null, photo);

        HttpResponse<String> operatorAttach = attachToControl(control, OPERATOR_OF_A, null, photo);
        HttpResponse<String> operatorList = getHttp(controlEvidence(control), OPERATOR_OF_A);
        HttpResponse<String> anonymous = postMultipart(EVIDENCE, null, newBoundary(), null, owner("controlId", control), photo);
        HttpResponse<String> otherAdminOfTheSameCompany = attachToControl(control, OTHER_ADMIN_OF_A, null, photo);

        assertThat(operatorAttach.statusCode()).isEqualTo(403);
        assertThat(operatorList.statusCode()).isEqualTo(403);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(otherAdminOfTheSameCompany.statusCode()).as("cualquier administrador de la empresa dueña").isEqualTo(201);
        assertThat(uploadRequests()).as("nada del operario llegó a Cloudinary").hasSize(2);
        assertThat(count("SELECT count(*) FROM evidence WHERE uploaded_by = ?", OPERATOR_A)).isZero();
    }

    @Test
    void aControlOrFailureOfAnotherCompanyOrUnknownIsNotFoundAndNothingIsUploaded() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        UUID failure = failureOf(inProgressControl());
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());

        HttpResponse<String> foreignControl = attachToControl(control, ADMIN_OF_B, null, photo);
        HttpResponse<String> unknownControl = attachToControl(UUID.randomUUID(), ADMIN_OF_A, null, photo);
        HttpResponse<String> foreignFailure = postMultipart(EVIDENCE, ADMIN_OF_B, newBoundary(), null, owner("failureId", failure), photo);
        HttpResponse<String> unknownFailure = postMultipart(EVIDENCE, ADMIN_OF_A, newBoundary(), null, owner("failureId", UUID.randomUUID()), photo);

        assertThat(foreignControl.statusCode()).isEqualTo(404);
        assertThat(body(foreignControl).path("code").asString()).isEqualTo("CONTROL_NOT_FOUND");
        assertThat(body(foreignControl).path("message")).isEqualTo(body(unknownControl).path("message"));
        assertThat(foreignFailure.statusCode()).isEqualTo(404);
        assertThat(body(foreignFailure).path("code").asString()).isEqualTo("FAILURE_NOT_FOUND");
        assertThat(unknownFailure.statusCode()).isEqualTo(404);
        assertThat(getHttp(controlEvidence(control), ADMIN_OF_B).statusCode()).isEqualTo(404);
        assertThat(getHttp(failureEvidence(failure), ADMIN_OF_B).statusCode()).isEqualTo(404);
        assertThat(uploadRequests()).isEmpty();
    }

    @Test
    void eachCompanySeesOnlyTheEvidenceItUploaded() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        attachToControl(control, ADMIN_OF_A, null, FilePart.files("a.jpg", "image/jpeg", jpeg()), FilePart.files("b.png", "image/png", png()));
        // una evidencia que subió otra empresa sobre el mismo control no se lista
        jdbc.sql("""
                INSERT INTO evidence (id, owner_type, owner_id, company_id, status, url, original_filename, content_type, size_bytes, uploaded_by, created_at, uploaded_at)
                VALUES (:id, 'CONTROL', :control, :other, 'UPLOADED', 'https://res.cloudinary.com/demo/ajena.jpg', 'ajena.jpg', 'image/jpeg', 10, :user, :at, :at)
                """).param("id", UUID.randomUUID()).param("control", control).param("other", COMPANY_B).param("user", ADMIN_B)
                .param("at", java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)).update();

        JsonNode listed = items(getHttp(controlEvidence(control), ADMIN_OF_A));

        assertThat(listed).extracting(i -> i.path("originalFilename").asString()).containsExactly("a.jpg", "b.png");
    }

    // ---- idempotencia ----

    @Test
    void aRetryWithTheSameKeyAndFilesButAnotherBoundaryReturnsTheSameResponseAndStoresNothingNew() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        String key = UUID.randomUUID().toString();
        List<FilePart> parts = List.of(owner("controlId", control), FilePart.files("a.jpg", "image/jpeg", jpeg()), FilePart.files("b.png", "image/png", png()));

        HttpResponse<String> first = postMultipart(EVIDENCE, ADMIN_OF_A, "----PrimerIntento1234567890", key, parts);
        HttpResponse<String> retry = postMultipart(EVIDENCE, ADMIN_OF_A, "----Reintento-con-otro-separador-9876543210", key, parts);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(retry.statusCode()).as("un reintento legítimo no es un 422").isEqualTo(201);
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(count("SELECT count(*) FROM evidence")).as("no se duplicó nada").isEqualTo(2);
        assertThat(uploadRequests()).as("ni se volvió a subir").hasSize(2);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'EVIDENCE_ATTACHED'")).isEqualTo(1);
    }

    @Test
    void theSameKeyWithOtherFilesOrAnotherControlIs422() throws Exception {
        stubUploadOk();
        UUID control = inProgressControl();
        String key = UUID.randomUUID().toString();
        attachToControl(control, ADMIN_OF_A, key, FilePart.files("a.jpg", "image/jpeg", jpeg()));

        HttpResponse<String> otherBytes = attachToControl(control, ADMIN_OF_A, key, FilePart.files("a.jpg", "image/jpeg", png()));
        HttpResponse<String> otherName = attachToControl(control, ADMIN_OF_A, key, FilePart.files("otra.jpg", "image/jpeg", jpeg()));
        HttpResponse<String> otherControl = attachToControl(inProgressControl(), ADMIN_OF_A, key, FilePart.files("a.jpg", "image/jpeg", jpeg()));

        for (HttpResponse<String> response : List.of(otherBytes, otherName, otherControl)) {
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(body(response).path("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(1);
    }

    @Test
    void theIdempotentReplayKeepsTheStatusItHadAndNeverUploadsAgain() throws Exception {
        stubUploadStatus(503, "{\"error\":{\"message\":\"Service Unavailable\"}}");
        UUID control = inProgressControl();
        String key = UUID.randomUUID().toString();
        FilePart photo = FilePart.files("a.jpg", "image/jpeg", jpeg());

        HttpResponse<String> first = attachToControl(control, ADMIN_OF_A, key, photo);
        clearStubs();
        stubUploadOk();
        HttpResponse<String> retry = attachToControl(control, ADMIN_OF_A, key, photo);

        assertThat(items(first).get(0).path("status").asString()).isEqualTo("PENDING");
        assertThat(body(retry)).as("la respuesta guardada, con el estado de entonces").isEqualTo(body(first));
        assertThat(uploadRequests()).as("solo el primer intento (el que falló) llegó a Cloudinary").hasSize(1);
        assertThat(count("SELECT count(*) FROM evidence")).isEqualTo(1);
    }
}
