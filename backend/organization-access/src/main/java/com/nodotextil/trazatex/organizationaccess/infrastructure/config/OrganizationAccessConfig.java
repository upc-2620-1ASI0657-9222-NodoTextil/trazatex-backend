package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.PublicEndpointMatcher;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyFilter;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxStorePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
@EnableScheduling
class OrganizationAccessConfig {

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
     * Idempotencia de las escrituras expuestas al frontend (ADR-010). Solo actúa si la petición trae {@code Idempotency-Key};
     * en los endpoints {@code @PublicEndpoint} (sin identidad) la ignora.
     */
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStorePort store,
                                                                PlatformTransactionManager transactionManager,
                                                                ObjectMapper objectMapper, Clock clock,
                                                                @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(
                new IdempotencyFilter(store, transactionManager, objectMapper, clock, IdempotencyFilter.DEFAULT_RETENTION,
                        new PublicEndpointMatcher(handlerMapping)));
        registration.addUrlPatterns("/api/*");
        registration.setName("idempotencyFilter");
        return registration;
    }
}
