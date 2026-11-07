package com.nodotextil.trazatex.production.infrastructure.config;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.BatchFactory;
import com.nodotextil.trazatex.production.domain.model.TransformationStrategy;
import com.nodotextil.trazatex.production.domain.service.DyeingStrategy;
import com.nodotextil.trazatex.production.domain.service.SpinningStrategy;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.production.domain.service.WeavingStrategy;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyFilter;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TransformationProperties.class)
class ProductionConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OutboxEventRecorder outboxEventRecorder(OutboxStorePort store, ObjectMapper objectMapper, Clock clock) {
        return new OutboxEventRecorder(store, objectMapper, clock);
    }

    /**
     * Idempotencia de las escrituras expuestas al frontend (ADR-010): solo actúa si la petición trae {@code Idempotency-Key}
     * (UUID) y {@code X-User-Id}. Production no tiene rutas públicas, así que nada se exceptúa. El comando interno de
     * Quality ({@code /internal/**}) tiene su propia idempotencia por {@code (batchId, requestId, operation)}.
     */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStorePort store, PlatformTransactionManager transactionManager,
                                                                ObjectMapper objectMapper, Clock clock) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(new IdempotencyFilter(store,
                transactionManager, objectMapper, clock, IdempotencyFilter.DEFAULT_RETENTION, request -> false));
        registration.addUrlPatterns("/api/*");
        registration.setName("idempotencyFilter");
        return registration;
    }

    /** Códigos aleatorios con {@code SecureRandom}; la fábrica sortea otro si el sorteado ya existe en la base. */
    @Bean
    BatchFactory batchFactory(BatchRepositoryPort batches) {
        return BatchFactory.secure(batches::isCodeTaken);
    }

    /** Una estrategia por tipo, con el mínimo configurado; un mínimo inválido o ausente impide arrancar. */
    @Bean
    TransformationFactory transformationFactory(TransformationProperties properties) {
        TransformationProperties.MinYieldPercent min = properties.minYieldPercent();
        if (min == null) {
            throw new IllegalStateException("Falta la configuración trazatex.transformation.min-yield-percent");
        }
        List<TransformationStrategy> strategies = List.of(
                strategy("spinning", min.spinning(), SpinningStrategy::new),
                strategy("weaving", min.weaving(), WeavingStrategy::new),
                strategy("dyeing", min.dyeing(), DyeingStrategy::new));
        return new TransformationFactory(strategies);
    }

    private static TransformationStrategy strategy(String name, BigDecimal minimum,
                                                   java.util.function.Function<BigDecimal, TransformationStrategy> constructor) {
        String property = "trazatex.transformation.min-yield-percent." + name;
        if (minimum == null) {
            throw new IllegalStateException("Falta la configuración " + property);
        }
        try {
            return constructor.apply(minimum);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Configuración inválida en " + property + ": " + e.getMessage(), e);
        }
    }
}
