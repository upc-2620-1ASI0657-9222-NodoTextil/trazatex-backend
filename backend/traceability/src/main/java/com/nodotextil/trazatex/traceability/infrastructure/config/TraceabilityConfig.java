package com.nodotextil.trazatex.traceability.infrastructure.config;

import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.traceability.domain.service.LineageAuthorizationPolicy;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
class TraceabilityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LineageAuthorizationPolicy lineageAuthorizationPolicy() {
        return new LineageAuthorizationPolicy();
    }

    @Bean
    OutboxEventRecorder outboxEventRecorder(OutboxStorePort store, ObjectMapper objectMapper, Clock clock) {
        return new OutboxEventRecorder(store, objectMapper, clock);
    }

    @Bean
    IdempotentEventHandler idempotentEventHandler(ProcessedEventPort processedEvents, PlatformTransactionManager transactionManager,
                                                  ObjectMapper objectMapper) {
        return new IdempotentEventHandler(processedEvents, transactionManager, objectMapper);
    }
}
