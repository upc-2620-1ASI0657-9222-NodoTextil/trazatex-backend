package com.nodotextil.trazatex.quality.acceptance;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** El servicio por HTTP real (Tomcat en un puerto aleatorio), con la identidad que inyecta el Gateway. Los pasos solo hablan con Quality por aquí. */
public class QualityApi {

    private final Environment environment;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    /** El puerto aleatorio se conoce cuando el servidor ya arrancó: se lee en el primer uso. */
    public QualityApi(Environment environment, ObjectMapper json) {
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

    /** Una parte de un formulario multipart: un campo de texto (sin {@code filename}) o un archivo. */
    public record Part(String name, String filename, String contentType, byte[] content) {
        public static Part field(String name, Object value) {
            return new Part(name, null, null, value.toString().getBytes(StandardCharsets.UTF_8));
        }

        public static Part file(String filename, String contentType, byte[] content) {
            return new Part("files", filename, contentType, content);
        }
    }

    public Reply postMultipart(Who who, String path, java.util.List<Part> parts) {
        String boundary = "----TrazaTex" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Part part : parts) {
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + part.name() + "\""
                    + (part.filename() == null ? "" : "; filename=\"" + part.filename() + "\"\r\nContent-Type: " + part.contentType()) + "\r\n\r\n";
            out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
            out.writeBytes(part.content());
            out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(identity(HttpRequest.newBuilder(URI.create(baseUrl() + path)), who).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())));
    }

    private HttpRequest.Builder jsonBody(HttpRequest.Builder request, Object body, String method) {
        String text = body == null ? "" : json.writeValueAsString(body);
        return request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(text));
    }

    private static HttpRequest.Builder identity(HttpRequest.Builder request, Who who) {
        request.header("X-User-Id", who.userId().toString()).header("X-User-Role", who.role());
        if (who.companyId() != null) {
            request.header("X-Company-Id", who.companyId().toString());
        }
        return request;
    }

    private Reply send(HttpRequest.Builder request) {
        try {
            HttpResponse<byte[]> response = http.send(request.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            boolean isJson = contentType.contains("json");
            String raw = isJson || bytes.length == 0 ? new String(bytes, StandardCharsets.UTF_8) : "<" + bytes.length + " bytes " + contentType + ">";
            JsonNode body = !isJson || raw.isBlank() ? null : json.readTree(raw);
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.isEmpty() ? "" : values.get(0)));
            return new Reply(response.statusCode(), body, raw, headers, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("la conexión con el servicio falló: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
