package com.nodotextil.trazatex.notification.infrastructure.scheduling;

import com.nodotextil.trazatex.notification.application.EmailDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Programa el envío de los correos diferidos; la lógica está en {@link EmailDeliveryService}. */
@Component
class MailDeliveryJob {
    private final EmailDeliveryService delivery;
    MailDeliveryJob(EmailDeliveryService delivery) { this.delivery=delivery; }

    @Scheduled(fixedDelayString="${trazatex.mail.job-delay:PT30S}", initialDelayString="PT10S")
    void deliver() { delivery.deliver(); }
}
