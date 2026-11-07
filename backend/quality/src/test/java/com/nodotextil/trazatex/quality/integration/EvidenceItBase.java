package com.nodotextil.trazatex.quality.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Evidencias contra un servidor real (Tomcat con su multipart, el filtro de idempotencia y el manejo de errores) y un WireMock en el
 * lugar de Cloudinary (ADR-007). Las peticiones multipart se arman a mano para poder elegir el separador. Reintentos cortos y circuito
 * pequeño para que las pruebas sean rápidas; la cola la recorre cada prueba invocando el caso de uso (el job programado está apagado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false", "trazatex.evidence.upload.enabled=false"})
abstract class EvidenceItBase extends FlowTestBase {

    static final String API_KEY = "123456789012345";
    static final String API_SECRET = "s3cr3t-d3-pru3ba-NO-DEBE-VIAJAR";
    static final String UPLOAD_PATH = "/v1_1/demo/image/upload";

    static final WireMockServer CLOUDINARY = new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        CLOUDINARY.start();
    }

    @DynamicPropertySource
    static void cloudinary(DynamicPropertyRegistry registry) {
        registry.add("trazatex.cloudinary.url", () -> "cloudinary://" + API_KEY + ":" + API_SECRET + "@demo");
        registry.add("trazatex.cloudinary.base-url", CLOUDINARY::baseUrl);
        registry.add("trazatex.cloudinary.read-timeout", () -> "PT2S");
        registry.add("trazatex.cloudinary.circuit-breaker.sliding-window-size", () -> "4");
        registry.add("trazatex.cloudinary.circuit-breaker.minimum-calls", () -> "2");
        registry.add("trazatex.cloudinary.circuit-breaker.failure-rate-threshold", () -> "50");
        registry.add("trazatex.cloudinary.circuit-breaker.wait-in-open-state", () -> "PT3S");
        registry.add("trazatex.cloudinary.circuit-breaker.half-open-calls", () -> "2");
        registry.add("trazatex.evidence.upload.max-attempts", () -> "3");
    }

    @LocalServerPort
    int port;
    @Autowired
    CircuitBreakerRegistry circuitBreakers;
    @Autowired
    com.nodotextil.trazatex.quality.application.port.in.UploadPendingEvidenceUseCase pendingUploads;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void resetCloudinary() {
        CLOUDINARY.resetAll();
        circuitBreakers.circuitBreaker("cloudinary").reset();
        circuitBreakers.circuitBreaker("production").reset();
    }

    // ---- Cloudinary simulado ----

    /** 200 con la URL de la imagen, que lleva el public_id recibido (plantilla de WireMock). */
    static void stubUploadOk() {
        CLOUDINARY.stubFor(post(urlPathEqualTo(UPLOAD_PATH)).atPriority(10).willReturn(okJson(
                "{\"public_id\":\"{{request.parts.public_id.body}}\",\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/{{request.parts.public_id.body}}.jpg\"}")
                .withTransformers("response-template")));
    }

    static void stubUploadStatus(int status, String body) {
        CLOUDINARY.stubFor(post(urlPathEqualTo(UPLOAD_PATH)).atPriority(1).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    static void stubUploadDelayed(int delayMillis) {
        CLOUDINARY.stubFor(post(urlPathEqualTo(UPLOAD_PATH)).atPriority(1).willReturn(okJson(
                "{\"public_id\":\"{{request.parts.public_id.body}}\",\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/{{request.parts.public_id.body}}.jpg\"}")
                .withTransformers("response-template").withFixedDelay(delayMillis)));
    }

    static void clearStubs() {
        CLOUDINARY.resetMappings();
    }

    static List<LoggedRequest> uploadRequests() {
        return CLOUDINARY.findAll(postRequestedFor(urlPathEqualTo(UPLOAD_PATH)));
    }

    static String part(LoggedRequest request, String name) {
        return request.getParts().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow()
                .getBody().asString();
    }

    static byte[] partBytes(LoggedRequest request, String name) {
        return request.getParts().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow().getBody().asBytes();
    }

    static boolean hasPart(LoggedRequest request, String name) {
        return request.getParts() != null && request.getParts().stream().map(Request.Part::getName).anyMatch(name::equals);
    }

    /** La firma que Cloudinary esperaría: SHA-1 de los parámetros firmados, en orden, más el secreto. */
    static String expectedSignature(String publicId, String timestamp) {
        try {
            String toSign = "allowed_formats=jpg,png,webp&overwrite=false&public_id=" + publicId + "&timestamp=" + timestamp + API_SECRET;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- HTTP multipart a mano ----

    /** Una parte del formulario: un archivo (con nombre) o un campo de texto (sin nombre de archivo). */
    record FilePart(String field, String filename, String contentType, byte[] content) {
        static FilePart files(String filename, String contentType, byte[] content) {
            return new FilePart("files", filename, contentType, content);
        }

        static FilePart field(String name, Object value) {
            return new FilePart(name, null, null, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    static byte[] multipartBody(String boundary, List<FilePart> parts) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (FilePart part : parts) {
                String disposition = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + part.field() + "\""
                        + (part.filename() == null ? "" : "; filename=\"" + part.filename() + "\"\r\nContent-Type: " + part.contentType()) + "\r\n\r\n";
                out.write(disposition.getBytes(StandardCharsets.UTF_8));
                out.write(part.content());
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    HttpResponse<String> postMultipart(String path, Caller caller, String boundary, String idempotencyKey, FilePart... parts) {
        return postMultipart(path, caller, boundary, idempotencyKey, List.of(parts));
    }

    HttpResponse<String> postMultipart(String path, Caller caller, String boundary, String idempotencyKey, List<FilePart> parts) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, parts)));
        identity(request, caller);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request.build());
    }

    HttpResponse<String> postRaw(String path, Caller caller, String contentType, byte[] body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60)).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        identity(request, caller);
        return send(request.build());
    }

    HttpResponse<String> getHttp(String path, Caller caller) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(30)).GET();
        identity(request, caller);
        return send(request.build());
    }

    private static void identity(HttpRequest.Builder request, Caller caller) {
        if (caller == null) {
            return;
        }
        request.header("X-User-Id", caller.userId().toString()).header("X-User-Role", caller.role());
        if (caller.companyId() != null) {
            request.header("X-Company-Id", caller.companyId().toString());
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("la conexión se cortó antes de recibir la respuesta: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    static String newBoundary() {
        return "----TrazaTex" + UUID.randomUUID().toString().replace("-", "");
    }

    static final String EVIDENCE = "/api/quality/evidence";

    static FilePart owner(String kind, UUID id) {
        return FilePart.field(kind, id);
    }

    static String controlEvidence(UUID control) {
        return "/api/quality/controls/" + control + "/evidence";
    }

    static String failureEvidence(UUID failure) {
        return "/api/quality/failures/" + failure + "/evidence";
    }

    long count(String sql, Object... args) {
        return jdbc.sql(sql.replace("?", ":p")).param("p", args.length == 0 ? null : args[0]).query(Long.class).single();
    }

    /** Un control en curso de la empresa A (Production confirmó el inicio). */
    UUID inProgressControl() throws Exception {
        return startControl(seedAvailable());
    }

    /** Un control finalizado con falla: devuelve el id de la falla (el control es {@code failureControl}). */
    UUID failureOf(UUID control) throws Exception {
        addTest(control, "FAILED");
        ProductionStub.reset();
        ProductionStub.answersOk();
        postAs(ADMIN_OF_A, "/api/quality/controls/" + control + "/finish").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        ProductionStub.reset();
        UUID batch = control(control, "batch_id", UUID.class);
        return failureOfBatch(batch);
    }

    WireMock wireMockClient() {
        return new WireMock(CLOUDINARY.port());
    }
}
