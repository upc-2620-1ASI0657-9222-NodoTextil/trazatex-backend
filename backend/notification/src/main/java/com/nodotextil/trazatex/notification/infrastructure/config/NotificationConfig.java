package com.nodotextil.trazatex.notification.infrastructure.config;

import com.nodotextil.trazatex.notification.application.EmailDeliveryService;
import com.nodotextil.trazatex.notification.application.RecipientPolicy;
import com.nodotextil.trazatex.notification.application.port.out.DeferredEmailStorePort;
import com.nodotextil.trazatex.notification.application.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
class NotificationConfig {
    @Bean @ConditionalOnMissingBean Clock clock() { return Clock.systemUTC(); }

    @Bean
    EmailDeliveryService emailDeliveryService(DeferredEmailStorePort store, EmailSenderPort sender, Clock clock,
            @Value("${TRAZATEX_MAIL_BLOCKED_DOMAINS:}") String blockedDomains,
            @Value("${trazatex.mail.max-attempts:8}") int maxAttempts) {
        return new EmailDeliveryService(store, sender, new RecipientPolicy(blockedDomains), clock, maxAttempts);
    }
}
