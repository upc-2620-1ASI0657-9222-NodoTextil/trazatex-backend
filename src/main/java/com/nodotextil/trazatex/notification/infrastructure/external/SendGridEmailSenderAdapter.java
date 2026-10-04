package com.nodotextil.trazatex.notification.infrastructure.external;

import com.nodotextil.trazatex.notification.application.port.EmailSenderPort;
import com.nodotextil.trazatex.shared.external.ExternalServiceException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Production email delivery via SendGrid's REST API.
 * Active only when app.external-services.enabled=true (see application.yml).
 * When disabled, {@link DevEmailSenderAdapter} is used instead.
 */
@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "true")
public class SendGridEmailSenderAdapter implements EmailSenderPort {
    private final RestClient client; private final String apiKey; private final String fromEmail;
    public SendGridEmailSenderAdapter(@Value("${app.sendgrid.api-key}") String apiKey,
            @Value("${app.sendgrid.from-email}") String fromEmail) {
        if (apiKey.isBlank() || fromEmail.isBlank()) throw new ExternalServiceException("SendGrid configuration is required");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5)); factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://api.sendgrid.com").requestFactory(factory).build();
        this.apiKey = apiKey; this.fromEmail = fromEmail;
    }
    @Override public void send(String recipient, String subject, String message) {
        try {
            client.post().uri("/v3/mail/send").header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("personalizations", List.of(Map.of("to", List.of(Map.of("email", recipient)))),
                            "from", Map.of("email", fromEmail), "subject", subject,
                            "content", List.of(Map.of("type", "text/plain", "value", message))))
                    .retrieve().toBodilessEntity();
        } catch (Exception exception) { throw new ExternalServiceException("SendGrid delivery failed", exception); }
    }
}