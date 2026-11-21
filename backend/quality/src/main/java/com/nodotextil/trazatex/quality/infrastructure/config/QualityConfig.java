package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.usecase.PendingCommandSettings;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyFilter;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ControlStartProperties.class)
class QualityConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OutboxEventRecorder outboxEventRecorder(OutboxStorePort store, ObjectMapper objectMapper, Clock clock) {
        return new OutboxEventRecorder(store, objectMapper, clock);
    }

    @Bean
    PendingCommandSettings controlStartSettings(ControlStartProperties properties) {
        return properties.toSettings();
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStorePort store, PlatformTransactionManager transactionManager,
                                                                ObjectMapper objectMapper, Clock clock) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(new IdempotencyFilter(store,
                transactionManager, objectMapper, clock, IdempotencyFilter.DEFAULT_RETENTION, request -> false));
        registration.addUrlPatterns("/api/*");
        registration.setName("idempotencyFilter");
        return registration;
    }
}
