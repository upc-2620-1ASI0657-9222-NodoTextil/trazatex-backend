package com.nodotextil.trazatex.notification.infrastructure.external;

import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException.Kind;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra un servidor HTTP local: nunca el servicio real de SendGrid. */
class SendGridEmailSenderTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(202);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> payload = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private static final OutboundEmail MAIL = new OutboundEmail("destino@correo.controlado", "Asunto", "Cuerpo con token-secreto");

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/mail/send", exchange -> {
            requests.incrementAndGet();
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private SendGridEmailSender sender() { return new SendGridEmailSender("clave-local-de-prueba", "remitente@trazatex.example", url()); }
    private SendGridEmailSender sensitiveCircuit() {
        return new SendGridEmailSender("clave-local-de-prueba", "remitente@trazatex.example", url(),
                SendGridEmailSender.circuit(CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)));
    }
    private static Kind kindOf(SendGridEmailSender sender) {
        return org.junit.jupiter.api.Assertions.assertThrows(EmailDeliveryException.class, () -> sender.send(MAIL)).kind();
    }

    @Test void anAcceptedEmailReachesTheProviderWithTheKeyAndTheSender() {
        sender().send(MAIL);
        assertThat(requests).hasValue(1);
        assertThat(authorization.get()).isEqualTo("Bearer clave-local-de-prueba");
        assertThat(payload.get()).contains("destino@correo.controlado", "remitente@trazatex.example", "TrazaTex", "Asunto");
    }

    @Test void aClientErrorIsPermanentAndRateLimitingOrServerErrorsAreTransient() {
        var sender = sender();
        status.set(401);
        assertThat(kindOf(sender)).isEqualTo(Kind.PERMANENT);
        status.set(400);
        assertThat(kindOf(sender)).isEqualTo(Kind.PERMANENT);
        status.set(429);
        assertThat(kindOf(sender)).isEqualTo(Kind.TRANSIENT);
        status.set(408);
        assertThat(kindOf(sender)).isEqualTo(Kind.TRANSIENT);
        status.set(503);
        assertThat(kindOf(sender)).isEqualTo(Kind.TRANSIENT);
    }

    @Test void theFailureReasonNamesTheStatusAndNothingElse() {
        status.set(403);
        assertThatThrownBy(() -> sender().send(MAIL)).isInstanceOfSatisfying(EmailDeliveryException.class, e -> {
            assertThat(e.reason()).isEqualTo("SENDGRID_HTTP_403");
            assertThat(e.getMessage()).doesNotContain("token-secreto", "clave-local-de-prueba");
        });
    }

    @Test void anUnexpectedSuccessCodeIsNotAcceptance() {
        status.set(200);
        assertThat(kindOf(sender())).isEqualTo(Kind.TRANSIENT);
    }

    @Test void aProviderThatIsDownIsTransient() {
        int port = server.getAddress().getPort();
        server.stop(0);
        var sender = new SendGridEmailSender("clave-local-de-prueba", "remitente@trazatex.example", "http://127.0.0.1:" + port);
        assertThat(kindOf(sender)).isEqualTo(Kind.TRANSIENT);
    }

    @Test void providerFailuresOpenTheCircuitButRejectedCredentialsDoNot() {
        var failing = sensitiveCircuit();
        status.set(503);
        for (int i = 0; i < 4; i++) assertThat(kindOf(failing)).isEqualTo(Kind.TRANSIENT);
        int before = requests.get();
        assertThat(kindOf(failing)).isEqualTo(Kind.UNAVAILABLE);
        assertThat(requests).hasValue(before);

        var rejected = sensitiveCircuit();
        status.set(401);
        for (int i = 0; i < 12; i++) assertThat(kindOf(rejected)).isEqualTo(Kind.PERMANENT);
        status.set(202);
        rejected.send(MAIL);
    }

    @Test void withoutKeyOrSenderTheProviderIsNotConfigured() {
        assertThat(new SendGridEmailSender("", "remitente@trazatex.example", url()).isConfigured()).isFalse();
        assertThat(new SendGridEmailSender("clave", "", url()).isConfigured()).isFalse();
        assertThat(sender().isConfigured()).isTrue();
    }
}
