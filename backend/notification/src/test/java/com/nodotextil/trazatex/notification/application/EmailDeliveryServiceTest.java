package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException.Kind;
import com.nodotextil.trazatex.notification.application.port.out.EmailSenderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryServiceTest {
    private static final int MAX_ATTEMPTS = 4;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private InMemoryDeferredEmailStore store;
    private FakeSender sender;
    private EmailDeliveryService service;

    static class FakeSender implements EmailSenderPort {
        boolean configured = true;
        Function<OutboundEmail, EmailDeliveryException> failure = mail -> null;
        final List<String> sent = new ArrayList<>();
        @Override public boolean isConfigured() { return configured; }
        @Override public void send(OutboundEmail email) {
            sent.add(email.to());
            var error = failure.apply(email);
            if (error != null) throw error;
        }
    }
    private static EmailDeliveryException error(Kind kind, String reason) { return new EmailDeliveryException(kind, reason, null); }

    @BeforeEach void setUp() {
        store = new InMemoryDeferredEmailStore(clock);
        sender = new FakeSender();
        service = new EmailDeliveryService(store, sender, new RecipientPolicy("bloqueado.pe"), clock, MAX_ATTEMPTS);
    }

    @Test void anAcceptedEmailIsSentAndItsBodyIsGone() {
        UUID id = store.add("persona@correo.controlado", 0);
        service.deliver();
        var row = store.rows.get(id);
        assertThat(row.status).isEqualTo("SENT");
        assertThat(row.body).isEmpty();
        assertThat(sender.sent).containsExactly("persona@correo.controlado");
    }

    @Test void aPermanentRejectionFailsAtOnceAndIsNeverRetried() {
        sender.failure = mail -> error(Kind.PERMANENT, "SENDGRID_HTTP_401");
        UUID id = store.add("persona@correo.controlado", 0);
        service.deliver();
        var row = store.rows.get(id);
        assertThat(row.status).isEqualTo("FAILED");
        assertThat(row.attempts).isEqualTo(1);
        assertThat(row.lastError).isEqualTo("SENDGRID_HTTP_401");
        assertThat(row.body).isEmpty();
        service.deliver();
        assertThat(sender.sent).hasSize(1);
    }

    @Test void aTransientFailureWaitsLongerEachTimeAndStaysPending() {
        sender.failure = mail -> error(Kind.TRANSIENT, "SENDGRID_HTTP_503");
        UUID first = store.add("a@correo.controlado", 0), second = store.add("b@correo.controlado", 1);
        service.deliver();
        assertThat(store.rows.get(first).status).isEqualTo("PENDING");
        assertThat(store.rows.get(first).attempts).isEqualTo(1);
        assertThat(store.rows.get(first).next).isEqualTo(clock.instant().plusSeconds(30));
        assertThat(store.rows.get(second).next).isEqualTo(clock.instant().plusSeconds(60));
        assertThat(store.rows.get(first).lastError).isEqualTo("SENDGRID_HTTP_503");
        assertThat(store.rows.get(first).body).isNotEmpty();
    }

    @Test void theWaitIsCappedAtOneHour() {
        sender.failure = mail -> error(Kind.TRANSIENT, "X");
        var big = new EmailDeliveryService(store, sender, new RecipientPolicy(""), clock, 100);
        UUID id = store.add("a@correo.controlado", 50);
        big.deliver();
        assertThat(store.rows.get(id).next).isEqualTo(clock.instant().plusSeconds(3600));
    }

    @Test void theRetryIsBoundedAndTheLastAttemptEndsInFailed() {
        sender.failure = mail -> error(Kind.TRANSIENT, "ResourceAccessException: SocketTimeoutException");
        UUID id = store.add("persona@correo.controlado", MAX_ATTEMPTS - 1);
        service.deliver();
        var row = store.rows.get(id);
        assertThat(row.status).isEqualTo("FAILED");
        assertThat(row.attempts).isEqualTo(MAX_ATTEMPTS);
        assertThat(row.lastError).startsWith("REINTENTOS_AGOTADOS: ");
        assertThat(row.body).isEmpty();
    }

    @Test void anOpenCircuitReturnsTheWholeBatchToTheQueueWithoutSpendingAttemptsOrCallingAgain() {
        sender.failure = mail -> error(Kind.UNAVAILABLE, "CIRCUITO_ABIERTO");
        UUID a = store.add("a@correo.controlado", 2), b = store.add("b@correo.controlado", 0);
        service.deliver();
        assertThat(sender.sent).hasSize(1);
        for (UUID id : List.of(a, b)) {
            assertThat(store.rows.get(id).status).isEqualTo("PENDING");
            assertThat(store.rows.get(id).next).isEqualTo(clock.instant().plusSeconds(30));
        }
        assertThat(store.rows.get(a).attempts).isEqualTo(2);
        assertThat(store.rows.get(b).attempts).isZero();
    }

    @Test void reservedAndBlockedDomainsAreNeverSent() {
        List<UUID> ids = new ArrayList<>();
        for (String email : List.of("persona@pima.example.com", "persona@example.org", "persona@empresa.invalid", "PERSONA@BLOQUEADO.PE"))
            ids.add(store.add(email, 0));
        UUID real = store.add("persona@correo.controlado", 0);
        service.deliver();
        for (UUID id : ids) {
            assertThat(store.rows.get(id).status).isEqualTo("UNDELIVERABLE");
            assertThat(store.rows.get(id).attempts).isZero();
            assertThat(store.rows.get(id).body).isEmpty();
        }
        assertThat(store.rows.get(real).status).isEqualTo("SENT");
        assertThat(sender.sent).containsExactly("persona@correo.controlado");
    }

    @Test void withoutProviderConfigurationNothingIsClaimedAndEmailsStayPendingWithTheirBody() {
        sender.configured = false;
        UUID id = store.add("persona@correo.controlado", 0);
        service.deliver();
        assertThat(store.claims).isZero();
        assertThat(store.expirations).isEqualTo(1);
        assertThat(store.rows.get(id).status).isEqualTo("PENDING");
        assertThat(store.rows.get(id).body).isNotEmpty();
    }

    @Test void anEmailAlreadyLeasedByAnotherRunIsNotSentTwice() {
        store.add("persona@correo.controlado", 0);
        service.deliver();
        service.deliver();
        assertThat(sender.sent).hasSize(1);
    }

    @Test void theMaximumNumberOfAttemptsMustBePositive() {
        assertThatThrownBy(() -> new EmailDeliveryService(store, sender, new RecipientPolicy(""), clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theOutboundBodyNeverShowsUpWhenTheEmailIsPrinted() {
        assertThat(new OutboundEmail("a@b.c", "Asunto", "token-secreto").toString()).doesNotContain("token-secreto");
        assertThat(Duration.ofMinutes(5)).isEqualTo(EmailDeliveryService.LEASE);
    }
}
