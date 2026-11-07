package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.application.exception.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidationCachePort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidatorPort;
import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Validación del RUC contra Chequea.pe (ADR-012; {@code GET /api/v1/ruc/{ruc}}, {@code Authorization: Bearer}). El dígito
 * verificador ya lo validó {@link Ruc}. Orden: caché reciente → API (timeout, circuit breaker) → respaldo.
 *
 * <ul>
 *   <li>{@code 200} con estado ACTIVO y condición HABIDO: se acepta y se guarda en {@code validated_ruc_cache}. Con cualquier
 *       otro estado o condición (BAJA, NO HABIDO...): se rechaza con {@code RUC_INVALID}; no se cachea.</li>
 *   <li>{@code 400} (formato) y {@code 404} (no existe): se rechaza con {@code RUC_INVALID}. No cuentan como fallo del servicio.</li>
 *   <li>Timeout, error de red, {@code 401/403/429/5xx}, circuito abierto o respuesta que no se entiende: la API "no responde".
 *       Un {@code 401/403} se registra como ERROR ("token de Chequea.pe inválido o vencido, revisar CHEQUEA_API_TOKEN") y un
 *       {@code 429} como WARN ("cuota de Chequea.pe agotada"); hacia el usuario es lo mismo que cualquier otra caída.
 *       Si hay una validación en caché (aunque sea antigua) se usa; si no, se rechaza con 503 {@code RUC_VALIDATION_UNAVAILABLE}
 *       y mensaje de reintento. <b>Nunca se acepta un RUC sin validar.</b></li>
 * </ul>
 * Si la API rechaza un RUC que estaba en caché, la entrada se borra. El token nunca se registra.
 */
public class ChequeaRucValidatorAdapter implements RucValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(ChequeaRucValidatorAdapter.class);
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_SHOWN = 40;

    private final RestClient client;
    private final CircuitBreaker breaker;
    private final RucValidationCachePort cache;
    private final ObjectMapper mapper;
    private final String token;
    private final Duration cacheTtl;
    private final Clock clock;

    public ChequeaRucValidatorAdapter(RestClient client, CircuitBreaker breaker, RucValidationCachePort cache,
                                      ObjectMapper mapper, String token, Duration cacheTtl, Clock clock) {
        this.client = client;
        this.breaker = breaker;
        this.cache = cache;
        this.mapper = mapper;
        this.token = token;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    @Override
    public void ensureActiveAndHabido(Ruc ruc) {
        Instant now = clock.instant();
        Optional<CachedRucValidation> cached = cache.find(ruc);
        if (cached.isPresent() && cached.get().isFresh(now, cacheTtl)) {
            return;
        }

        Verdict verdict;
        try {
            verdict = lookup(ruc);
        } catch (ChequeaUnavailableException | CallNotPermittedException e) {
            if (cached.isPresent()) {
                log.warn("Chequea.pe no respondió ({}); se acepta el RUC por su validación en caché del {}", e.getMessage(),
                        cached.get().validatedAt());
                return;
            }
            log.warn("Chequea.pe no respondió ({}) y el RUC no está en caché: se rechaza para que se reintente", e.getMessage());
            throw new ExternalServiceUnavailableException(ErrorCodes.RUC_VALIDATION_UNAVAILABLE,
                    "No se pudo validar el RUC en este momento. Inténtalo más tarde.");
        }

        if (verdict.accepted()) {
            cache.save(new CachedRucValidation(ruc, verdict.businessName(), verdict.status(), verdict.condition(), now));
            return;
        }
        cached.ifPresent(stale -> cache.delete(ruc));
        throw new ValidationException(ErrorCodes.RUC_INVALID, verdict.message(), List.of());
    }

    private Verdict lookup(Ruc ruc) {
        if (token == null || token.isBlank()) {
            throw new ChequeaUnavailableException("CHEQUEA_API_TOKEN no está configurado");
        }
        return breaker.executeSupplier(() -> fetch(ruc));
    }

    private Verdict fetch(Ruc ruc) {
        try {
            return client.get()
                    .uri("/api/v1/ruc/{ruc}", ruc.value())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> interpret(ruc, response.getStatusCode().value(), readBody(response.getBody())));
        } catch (RestClientException e) {
            throw new ChequeaUnavailableException(e.getClass().getSimpleName());
        }
    }

    private static String readBody(java.io.InputStream body) throws IOException {
        return new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
    }

    private Verdict interpret(Ruc ruc, int status, String body) {
        switch (status) {
            case 200:
                return parseOk(ruc, body);
            case 400:
                return Verdict.rejected("El RUC no es válido: no cumple el formato de un RUC.");
            case 404:
                return Verdict.rejected("El RUC no es válido: no existe.");
            case 401, 403:
                log.error("Token de Chequea.pe inválido o vencido, revisar CHEQUEA_API_TOKEN (HTTP {})", status);
                throw new ChequeaUnavailableException("HTTP " + status);
            case 429:
                log.warn("Cuota de Chequea.pe agotada (HTTP 429)");
                throw new ChequeaUnavailableException("HTTP 429");
            default:
                throw new ChequeaUnavailableException("HTTP " + status);
        }
    }

    private Verdict parseOk(Ruc ruc, String body) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (JacksonException e) {
            throw new ChequeaUnavailableException("respuesta que no es JSON");
        }
        String answeredRuc = text(json, "ruc");
        String status = text(json, "estado");
        String condition = text(json, "condicion");
        if (!ruc.value().equals(answeredRuc) || status == null || condition == null) {
            throw new ChequeaUnavailableException("respuesta inesperada");
        }
        if (status.equalsIgnoreCase("ACTIVO") && condition.equalsIgnoreCase("HABIDO")) {
            String name = text(json, "razonSocial");
            return Verdict.accepted(name == null ? "(sin razón social)" : name, status.toUpperCase(), condition.toUpperCase());
        }
        return Verdict.rejected("El RUC no es válido: el contribuyente debe estar ACTIVO y HABIDO (estado: "
                + shown(status) + ", condición: " + shown(condition) + ").");
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || !node.isString() || node.asString().isBlank() ? null : node.asString().trim();
    }

    /** Lo que viene de un tercero se acota y se limpia antes de ponerlo en un mensaje. */
    private static String shown(String value) {
        String clean = value.replaceAll("[^\\p{L}\\p{N} ]", "");
        return clean.length() <= MAX_SHOWN ? clean : clean.substring(0, MAX_SHOWN);
    }

    private record Verdict(boolean accepted, String businessName, String status, String condition, String message) {

        static Verdict accepted(String businessName, String status, String condition) {
            return new Verdict(true, businessName, status, condition, null);
        }

        static Verdict rejected(String message) {
            return new Verdict(false, null, null, null, message);
        }
    }

    /** Fallo técnico (cuenta para el circuit breaker): red, timeout, token, 429/5xx o respuesta incomprensible. */
    private static final class ChequeaUnavailableException extends RuntimeException {

        ChequeaUnavailableException(String message) {
            super(message, null, false, false);
        }
    }
}
