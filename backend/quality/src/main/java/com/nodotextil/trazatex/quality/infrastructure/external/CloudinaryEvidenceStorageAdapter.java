package com.nodotextil.trazatex.quality.infrastructure.external;

import com.nodotextil.trazatex.quality.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.quality.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.quality.application.exception.StorageTransientException;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceStoragePort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Subida firmada a Cloudinary ({@code POST /v1_1/{cloud}/image/upload}), con timeout y circuit breaker (ADR-007).
 *
 * <ul>
 *   <li><b>Idempotente</b>: {@code public_id} = id de la evidencia y {@code overwrite=false}; si la imagen ya existe (una subida
 *       anterior cuya respuesta se perdió) Cloudinary devuelve la existente en vez de duplicar.</li>
 *   <li>{@code allowed_formats=jpg,png,webp}: segunda barrera tras la validación por bytes.</li>
 *   <li>Clasificación: {@code 400/422} = el archivo no es una imagen válida (permanente, no cuenta para el circuito);
 *       timeout, red, {@code 5xx}, {@code 408/420/429}, {@code 401/403} (credenciales: se arregla configurando) o una respuesta
 *       que no se entiende = transitorio (cuenta para el circuito); sin credenciales o circuito abierto = no disponible.</li>
 *   <li>Nunca simula éxito: la URL sale solo de una respuesta 2xx de Cloudinary.</li>
 * </ul>
 */
public class CloudinaryEvidenceStorageAdapter implements EvidenceStoragePort {

    private static final String ALLOWED_FORMATS = "jpg,png,webp";

    private final RestClient client;
    private final CircuitBreaker breaker;
    private final CloudinaryCredentials credentials;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CloudinaryEvidenceStorageAdapter(RestClient client, CircuitBreaker breaker, CloudinaryCredentials credentials,
                                            ObjectMapper mapper, Clock clock) {
        this.client = client;
        this.breaker = breaker;
        this.credentials = credentials;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UploadedImage upload(String publicId, String filename, String contentType, byte[] content) {
        if (credentials == null) {
            throw new StorageNotAvailableException("Cloudinary no está configurado (CLOUDINARY_URL)");
        }
        try {
            Outcome outcome = breaker.executeSupplier(() -> send(publicId, filename, content));
            if (outcome.rejection() != null) {
                throw new StoragePermanentException(outcome.rejection());
            }
            return new UploadedImage(outcome.url());
        } catch (CallNotPermittedException open) {
            throw new StorageNotAvailableException("el circuito de Cloudinary está abierto");
        }
    }

    @Override
    public void delete(String publicId) {
        if (credentials == null) throw new StorageNotAvailableException("Cloudinary no está configurado.");
        long timestamp=clock.instant().getEpochSecond();
        String signature;
        try {
            String value="invalidate=true&public_id="+publicId+"&timestamp="+timestamp+credentials.apiSecret();
            signature=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        MultiValueMap<String,String> form=new LinkedMultiValueMap<>();
        form.add("public_id",publicId); form.add("timestamp",Long.toString(timestamp)); form.add("invalidate","true");
        form.add("api_key",credentials.apiKey()); form.add("signature",signature);
        // M5 / ADR-007: borrar también pasa por el circuit breaker de Cloudinary (con el circuito abierto no se llama y se reintenta luego)
        try {
            breaker.executeRunnable(() -> {
                try {
                    client.post().uri("/v1_1/{cloud}/image/destroy",credentials.cloudName()).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                            .exchange((request,response) -> {
                                int status=response.getStatusCode().value();
                                if (status < 200 || status >= 300) throw new StorageTransientException("No se pudo eliminar la imagen (HTTP "+status+").",null);
                                String body=new String(response.getBody().readNBytes(16384),StandardCharsets.UTF_8);
                                String result=mapper.readTree(body).path("result").asString();
                                if (!"ok".equals(result) && !"not found".equals(result)) throw new StorageTransientException("El almacén no confirmó la eliminación.",null);
                                return true;
                            });
                } catch (RestClientException error) { throw new StorageTransientException("El almacén no respondió al eliminar la imagen.",error); }
            });
        } catch (CallNotPermittedException open) {
            throw new StorageNotAvailableException("el circuito de Cloudinary está abierto");
        }
    }

    private record Outcome(String url, String rejection) {
    }

    private Outcome send(String publicId, String filename, byte[] content) {
        long timestamp = clock.instant().getEpochSecond();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("api_key", credentials.apiKey());
        form.add("timestamp", Long.toString(timestamp));
        form.add("public_id", publicId);
        form.add("overwrite", "false");
        form.add("allowed_formats", ALLOWED_FORMATS);
        form.add("signature", sign(publicId, timestamp));
        try {
            return client.post().uri("/v1_1/{cloud}/image/upload", credentials.cloudName())
                    .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON).body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String body = new String(response.getBody().readNBytes(256 * 1024), StandardCharsets.UTF_8);
                        if (status >= 200 && status < 300) {
                            return new Outcome(secureUrl(body), null);
                        }
                        if (status == 400 || status == 422) {
                            return new Outcome(null, "HTTP " + status + ": " + errorMessage(body));
                        }
                        throw new StorageTransientException("HTTP " + status + ": " + errorMessage(body), null);
                    });
        } catch (RestClientException e) {
            throw new StorageTransientException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Firma de Cloudinary: SHA-1 de los parámetros firmados, en orden alfabético, más el secreto. */
    private String sign(String publicId, long timestamp) {
        String toSign = "allowed_formats=" + ALLOWED_FORMATS + "&overwrite=false&public_id=" + publicId + "&timestamp=" + timestamp
                + credentials.apiSecret();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String secureUrl(String body) {
        try {
            JsonNode url = mapper.readTree(body).path("secure_url");
            if (url.isString() && url.asString().startsWith("https://")) {
                return url.asString();
            }
        } catch (RuntimeException ignored) {
            // cae al fallo transitorio de abajo
        }
        throw new StorageTransientException("Cloudinary respondió 2xx sin una secure_url entendible", null);
    }

    private String errorMessage(String body) {
        try {
            JsonNode message = mapper.readTree(body).path("error").path("message");
            if (message.isString()) {
                return message.asString();
            }
        } catch (RuntimeException ignored) {
            // cuerpo que no es JSON
        }
        return "sin detalle";
    }
}
