package com.nodotextil.trazatex.notification.infrastructure.external;
import com.nodotextil.trazatex.notification.application.port.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false", matchIfMissing = true)
public class DevEmailSenderAdapter implements EmailSenderPort {
    private static final Logger log = LoggerFactory.getLogger(DevEmailSenderAdapter.class);
    @Override public void send(String recipient, String subject, String message) {
        log.info("DEV email to={} subject={} message={}", recipient, subject, message);
    }
}