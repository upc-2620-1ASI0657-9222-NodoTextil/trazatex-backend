package com.nodotextil.trazatex.quality.infrastructure.external;

import com.nodotextil.trazatex.quality.application.port.out.PhaseCommand;
import com.nodotextil.trazatex.quality.application.port.out.PhaseCommandOutcome;
import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code POST /internal/batches/{batchId}/phase-transitions} (ADR-009, docs/architecture/communication.md). Orden de las defensas, de afuera
 * hacia adentro: <b>Retry</b> (2 intentos en total, solo ante 5xx, timeout o error de red) → <b>Circuit Breaker</b> → llamada con timeout.
 * Cada intento usa el mismo {@code requestId}, y por eso repetir es seguro: Production es idempotente por {@code (batchId, requestId,
 * operation)}.
 *
 * <p>Qué cuenta como fallo para el circuit breaker: lo que no es una respuesta de Production (5xx, timeout, sin conexión). Un 409, 404 o
 * 400 es Production respondiendo con normalidad: no abre el circuito ni se reintenta.
 *
 * <p><b>No hay fallback que simule éxito</b>: si Production no responde el resultado es {@link PhaseCommandOutcome.Unavailable}, y quien
 * llama decide (el control sigue {@code STARTING} y el job lo repite). {@code Retry-After} del 503 no se usa: el retry de la petición
 * espera lo configurado y el job tiene su propia espera creciente.
 */
public class ProductionPhaseClient implements ProductionPhasePort {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    /** Production no respondió con una decisión: se puede reintentar con el mismo {@code requestId}. */
    private static final class TransientProductionException extends RuntimeException {
        TransientProductionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final RestClient client;
    private final CircuitBreaker breaker;
    private final Retry retry;
    private final ObjectMapper mapper;

    public ProductionPhaseClient(RestClient client, CircuitBreaker breaker, ProductionClientProperties.Retry retrySettings, ObjectMapper mapper) {
        this.client = client;
        this.breaker = breaker;
        this.mapper = mapper;
        this.retry = Retry.of("production-phase", RetryConfig.custom()
                .maxAttempts(retrySettings.maxAttempts())
                .waitDuration(retrySettings.waitDuration())
                .retryOnException(e -> e instanceof TransientProductionException)
                .build());
    }

    @Override
    public PhaseCommandOutcome send(PhaseCommand command) {
        Supplier<PhaseCommandOutcome> guarded = CircuitBreaker.decorateSupplier(breaker, () -> attempt(command));
        try {
            return Retry.decorateSupplier(retry, guarded).get();
        } catch (CallNotPermittedException open) {
            return new PhaseCommandOutcome.Unavailable("el circuito de Production está abierto", true);
        } catch (TransientProductionException e) {
            return new PhaseCommandOutcome.Unavailable(e.getMessage(), false);
        }
    }

    private PhaseCommandOutcome attempt(PhaseCommand command) {
        String body = body(command);
        try {
            return client.post().uri("/internal/batches/{batchId}/phase-transitions", command.batchId())
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body)
                    .exchange((request, response) -> interpret(response.getStatusCode().value(),
                            new String(response.getBody().readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new TransientProductionException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private String body(PhaseCommand command) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("operation", command.operation().name());
        json.put("requestId", command.requestId());
        if (command.qualityHold() != null) {
            json.put("qualityHold", command.qualityHold());
        }
        if (command.reason() != null) {
            json.put("reason", command.reason());
        }
        if (command.requestedBy() != null) {
            json.put("requestedBy", command.requestedBy().toString());
        }
        return mapper.writeValueAsString(json);
    }

    /** La tabla «Respuestas y cómo debe reaccionar Quality» de communication.md. */
    private PhaseCommandOutcome interpret(int status, String body) {
        if (status == 200) {
            return confirmed(body);
        }
        if (status == 409 || status == 404) {
            return new PhaseCommandOutcome.Rejected(status, errorCode(body, "HTTP_" + status), errorMessage(body, status));
        }
        if (status >= 500) {
            throw new TransientProductionException("HTTP " + status + " " + errorCode(body, ""), null);
        }
        if (status >= 400) {
            return new PhaseCommandOutcome.Defective(status, errorCode(body, "HTTP_" + status), errorMessage(body, status));
        }
        // 1xx, 3xx u otro 2xx: no es una respuesta del contrato y no se sabe si se aplicó
        throw new TransientProductionException("respuesta inesperada HTTP " + status, null);
    }

    private PhaseCommandOutcome confirmed(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            return new PhaseCommandOutcome.Confirmed(UUID.fromString(json.path("batchId").asString()),
                    BatchPhase.valueOf(json.path("phase").asString()), json.path("qualityHold").asBoolean(false));
        } catch (RuntimeException e) {
            throw new TransientProductionException("HTTP 200 con un cuerpo ilegible: " + e.getMessage(), e);
        }
    }

    /** El {@code ApiError.code} del cuerpo, acotado a lo que cabe en {@code quality_control.cancel_reason}. */
    private String errorCode(String body, String fallback) {
        String code = text(body, "code");
        if (code == null || code.isBlank()) {
            return fallback;
        }
        return code.length() > 100 ? code.substring(0, 100) : code;
    }

    private String errorMessage(String body, int status) {
        String message = text(body, "message");
        return message == null || message.isBlank() ? "HTTP " + status : message;
    }

    private String text(String body, String field) {
        try {
            JsonNode node = mapper.readTree(body).path(field);
            return node.isString() ? node.asString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
