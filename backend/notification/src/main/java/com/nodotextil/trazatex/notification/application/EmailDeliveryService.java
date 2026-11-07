package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.application.dto.DueEmail;
import com.nodotextil.trazatex.notification.application.dto.OutboundEmail;
import com.nodotextil.trazatex.notification.application.port.out.DeferredEmailStorePort;
import com.nodotextil.trazatex.notification.application.port.out.EmailDeliveryException;
import com.nodotextil.trazatex.notification.application.port.out.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Envío de los correos diferidos (ADR-007). Un correo nunca se pierde: queda {@code PENDING} hasta que el proveedor lo acepta. El Retry está
 * <b>acotado</b>: cada fallo transitorio (429, 5xx, tiempo agotado) suma un intento y espera más (30 s, 60 s, … hasta 1 h); al llegar a
 * {@code maxAttempts} pasa a {@code FAILED}. Un 4xx permanente (clave inválida, remitente sin verificar) pasa a {@code FAILED} de inmediato, sin
 * reintentar. Con el circuito abierto no se llama al proveedor ni se gastan intentos. El destinatario se resolvió al crear el correo; este
 * servicio no recorre las notificaciones.
 */
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    static final int BATCH_SIZE = 10;
    static final Duration LEASE = Duration.ofMinutes(5);
    static final long MAX_DELAY_SECONDS = 3600;
    static final int MAX_REASON = 200;

    private final DeferredEmailStorePort store;
    private final EmailSenderPort sender;
    private final RecipientPolicy policy;
    private final Clock clock;
    private final int maxAttempts;

    public EmailDeliveryService(DeferredEmailStorePort store, EmailSenderPort sender, RecipientPolicy policy, Clock clock, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts debe ser 1 o mayor");
        this.store = store;
        this.sender = sender;
        this.policy = policy;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public void deliver() {
        store.expireDue();
        if (!sender.isConfigured()) return;
        List<DueEmail> due = store.claimDue(BATCH_SIZE, LEASE);
        for (int i = 0; i < due.size(); i++) {
            DueEmail mail = due.get(i);
            if (policy.isBlocked(mail.recipient())) {
                store.markUndeliverable(mail.id(), "Envío externo deshabilitado para esta dirección.");
                continue;
            }
            try {
                sender.send(new OutboundEmail(mail.recipient(), mail.subject(), mail.body()));
                store.markSent(mail.id());
            } catch (EmailDeliveryException e) {
                if (e.kind() == EmailDeliveryException.Kind.UNAVAILABLE) {
                    Instant retry = clock.instant().plusSeconds(30);
                    for (DueEmail rest : due.subList(i, due.size())) store.release(rest.id(), retry);
                    log.warn("Circuito de correo abierto: {} correos vuelven a la cola sin gastar intentos", due.size() - i);
                    return;
                }
                fail(mail, e);
            }
        }
    }

    private void fail(DueEmail mail, EmailDeliveryException e) {
        int attempt = mail.attempts() + 1;
        if (e.kind() == EmailDeliveryException.Kind.PERMANENT) {
            store.markFailed(mail.id(), cut(e.reason()));
            log.warn("Correo {} rechazado de forma permanente; {}", mail.id(), e.reason());
        } else if (attempt >= maxAttempts) {
            store.markFailed(mail.id(), cut("REINTENTOS_AGOTADOS: " + e.reason()));
            log.warn("Correo {} sin enviar tras {} intentos; {}", mail.id(), attempt, e.reason());
        } else {
            long delay = Math.min(MAX_DELAY_SECONDS, 30L << Math.min(mail.attempts(), 7));
            store.markRetry(mail.id(), clock.instant().plusSeconds(delay), cut(e.reason()));
            log.warn("Correo {} pendiente; intento {} de {}; {}", mail.id(), attempt, maxAttempts, e.reason());
        }
    }

    private static String cut(String reason) { return reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON); }
}
