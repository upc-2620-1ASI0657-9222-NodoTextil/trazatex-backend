package com.nodotextil.trazatex.notification.infrastructure.external;

import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException.Kind;
import com.nodotextil.trazatex.notification.application.port.out.EmailSenderPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SendGrid detrás de {@link EmailSenderPort} (ADR-007): conexión de 2 s, lectura de 8 s y circuit breaker. Clasifica cada respuesta:
 * 429, 408 y 5xx son transitorios; el resto de 4xx es permanente y <b>no cuenta para el circuito</b> (una clave inválida no es una caída del
 * proveedor). Ni el cuerpo del correo (puede llevar un token) ni la respuesta del proveedor llegan a un log ni a {@code last_error}.
 */
@Component
public class SendGridEmailSender implements EmailSenderPort {
    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);

    /** Una respuesta 4xx permanente: se ignora en el circuito. */
    static final class PermanentRejection extends RuntimeException {
        final int status;
        PermanentRejection(int status) { super("SENDGRID_HTTP_" + status, null, false, false); this.status = status; }
    }

    private final String apiKey;
    private final String from;
    private final RestClient client;
    private final CircuitBreaker circuit;

    @Autowired
    public SendGridEmailSender(@Value("${SENDGRID_API_KEY:}") String apiKey, @Value("${SENDGRID_FROM_EMAIL:}") String from,
                               @Value("${SENDGRID_BASE_URL:https://api.sendgrid.com}") String baseUrl) {
        this(apiKey, from, baseUrl, defaultCircuit());
    }

    SendGridEmailSender(String apiKey, String from, String baseUrl, CircuitBreaker circuit) {
        this.apiKey = apiKey;
        this.from = from;
        this.circuit = circuit;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
        if (!isConfigured()) log.warn("SendGrid sin configurar: los correos permanecerán pendientes. Configure SENDGRID_API_KEY y SENDGRID_FROM_EMAIL.");
    }

    static CircuitBreaker circuit(CircuitBreakerConfig.Builder builder) {
        return CircuitBreaker.of("sendgrid", builder.ignoreExceptions(PermanentRejection.class).build());
    }
    private static CircuitBreaker defaultCircuit() { return circuit(CircuitBreakerConfig.custom()); }

    @Override public boolean isConfigured() { return !apiKey.isBlank() && !from.isBlank(); }

    @Override public void send(OutboundEmail mail) {
        try {
            circuit.executeRunnable(() -> post(mail));
        } catch (CallNotPermittedException e) {
            throw new EmailDeliveryException(Kind.UNAVAILABLE, "CIRCUITO_ABIERTO", e);
        } catch (PermanentRejection e) {
            throw new EmailDeliveryException(Kind.PERMANENT, "SENDGRID_HTTP_" + e.status, e);
        } catch (RestClientResponseException e) {
            throw new EmailDeliveryException(Kind.TRANSIENT, "SENDGRID_HTTP_" + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new EmailDeliveryException(Kind.TRANSIENT, "ResourceAccessException: " + e.getMostSpecificCause().getClass().getSimpleName(), e);
        } catch (RuntimeException e) {
            throw new EmailDeliveryException(Kind.TRANSIENT, e.getClass().getSimpleName(), e);
        }
    }

    private void post(OutboundEmail mail) {
        try {
            var response = client.post().uri("/v3/mail/send").header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("personalizations", List.of(Map.of("to", List.of(Map.of("email", mail.to())))),
                            "from", Map.of("email", from, "name", "TrazaTex"), "subject", mail.subject(),
                            "content", List.of(Map.of("type", "text/plain", "value", mail.body()))))
                    .retrieve().toBodilessEntity();
            if (response.getStatusCode().value() != 202) throw new IllegalStateException("SendGrid no aceptó el correo.");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 429) throw new PermanentRejection(status);
            throw e;
        }
    }
}
