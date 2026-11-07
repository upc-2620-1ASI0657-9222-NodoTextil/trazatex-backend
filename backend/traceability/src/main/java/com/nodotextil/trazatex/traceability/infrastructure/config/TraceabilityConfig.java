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

/**
 * Los puertos del shared-kernel sobre Neo4j (ADR-004). {@code PlatformTransactionManager} es el
 * {@code Neo4jTransactionManager} (el único del servicio): {@link IdempotentEventHandler} abre con él la transacción en la que
 * se registran el {@code :ProcessedEvent}, los cambios del grafo y el {@code :OutboxEvent}, y las tres cosas se confirman o
 * se revierten juntas.
 */
@Configuration
class TraceabilityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** La privacidad de cada empresa viaja en la propia consulta ({@code Lineage.companyPrivacy}), leída del grafo con su configuración vigente: la política no guarda estado. */
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
