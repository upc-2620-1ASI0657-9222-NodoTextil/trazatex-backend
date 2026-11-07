package com.nodotextil.trazatex.production.acceptance;

import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * El servicio por HTTP real (Tomcat en un puerto aleatorio), con la identidad que inyecta el Gateway. Los pasos de los escenarios
 * solo hablan con Production por aquí: ni MockMvc ni los casos de uso.
 */
public class ProductionApi {

    /** Una parte de un {@code multipart/form-data}. */
    public record Part(String field, String filename, String contentType, byte[] content) {
    }

    private final Environment environment;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    /** El puerto aleatorio se conoce cuando el servidor ya arrancó (después de crear los beans): se lee en el primer uso. */
    public ProductionApi(Environment environment, ObjectMapper json) {
        this.environment = environment;
        this.json = json;
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getRequiredProperty("local.server.port");
    }

    public Reply get(Who who, String path) {
        return send(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who).GET());
    }

    public Reply post(Who who, String path, Object body) {
        return send(jsonBody(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who), body, "POST"));
    }

    public Reply put(Who who, String path, Object body) {
        return send(jsonBody(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who), body, "PUT"));
    }

    public Reply patch(Who who, String path, Object body) {
        return send(jsonBody(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who), body, "PATCH"));
    }

    /** El comando interno de Quality: red interna, sin identidad. */
    public Reply internal(String path, Object body) {
        return send(jsonBody(HttpRequest.newBuilder(URI.create(baseUrl() + path)), body, "POST"));
    }

    public Reply multipart(Who who, String path, List<Part> parts) {
        String boundary = "----Aceptacion" + UUID.randomUUID().toString().replace("-", "");
        return send(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, parts))));
    }

    private HttpRequest.Builder jsonBody(HttpRequest.Builder request, Object body, String method) {
        String text = body == null ? "" : body instanceof String raw ? raw : json.writeValueAsString(body);
        return request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(text));
    }

    private static HttpRequest.Builder identity(HttpRequest.Builder request, Who who) {
        if (who != null) {
            request.header("X-User-Id", who.userId().toString()).header("X-User-Role", who.role());
            if (who.companyId() != null) {
                request.header("X-Company-Id", who.companyId().toString());
            }
        }
        return request;
    }

    private Reply send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            JsonNode body = raw == null || raw.isBlank() ? null : json.readTree(raw);
            return new Reply(response.statusCode(), body, raw);
        } catch (IOException e) {
            throw new IllegalStateException("la conexión con el servicio falló: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static byte[] multipartBody(String boundary, List<Part> parts) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Part part : parts) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + part.field() + "\"; filename=\"" + part.filename()
                        + "\"\r\nContent-Type: " + part.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(part.content());
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
