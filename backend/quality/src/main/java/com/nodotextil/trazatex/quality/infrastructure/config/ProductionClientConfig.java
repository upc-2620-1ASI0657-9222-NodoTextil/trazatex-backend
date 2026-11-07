package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.port.out.ProductionPhasePort;
import com.nodotextil.trazatex.quality.infrastructure.external.ExternalHttpClients;
import com.nodotextil.trazatex.quality.infrastructure.external.ProductionClientProperties;
import com.nodotextil.trazatex.quality.infrastructure.external.ProductionPhaseClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** El comando interno de Production detrás de su puerto, con timeout, circuit breaker y retry (ADR-007, ADR-009). */
@Configuration
@EnableConfigurationProperties(ProductionClientProperties.class)
class ProductionClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductionClientConfig.class);

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    ProductionPhasePort productionPhasePort(ProductionClientProperties properties, CircuitBreakerRegistry registry, ObjectMapper mapper) {
        CircuitBreaker breaker = registry.circuitBreaker("production", properties.circuitBreaker().toConfig());
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Circuit breaker 'production': {}", event.getStateTransition()));
        return new ProductionPhaseClient(ExternalHttpClients.restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()),
                breaker, properties.retry(), mapper);
    }
}
