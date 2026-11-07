package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidationCachePort;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaProperties;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.ChequeaRucValidatorAdapter;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.CircuitBreakerSettings;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.ExternalHttpClients;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.HibpPasswordBreachCheckerAdapter;
import com.nodotextil.trazatex.organizationaccess.infrastructure.external.HibpProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Adaptadores de sistemas externos (Chequea.pe y HIBP) con timeout y Resilience4j; cada uno detrás de su puerto. */
@Configuration
@EnableConfigurationProperties({ChequeaProperties.class, HibpProperties.class})
class ExternalServicesConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalServicesConfig.class);

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    ChequeaRucValidatorAdapter chequeaRucValidator(ChequeaProperties properties, CircuitBreakerRegistry registry,
                                                    RucValidationCachePort cache, ObjectMapper mapper, Clock clock) {
        if (properties.token() == null || properties.token().isBlank()) {
            log.warn("CHEQUEA_API_TOKEN no está configurado: no se podrá validar ningún RUC que no esté en caché (ADR-012)");
        }
        return new ChequeaRucValidatorAdapter(ExternalHttpClients.restClient(properties.baseUrl(), properties.timeout()),
                breaker(registry, "chequea", properties.circuitBreaker()), cache, mapper, properties.token(),
                properties.cacheTtl(), clock);
    }

    @Bean
    HibpPasswordBreachCheckerAdapter hibpPasswordBreachChecker(HibpProperties properties, CircuitBreakerRegistry registry) {
        return new HibpPasswordBreachCheckerAdapter(ExternalHttpClients.restClient(properties.baseUrl(), properties.timeout()),
                breaker(registry, "hibp", properties.circuitBreaker()), properties.enabled());
    }

    private static CircuitBreaker breaker(CircuitBreakerRegistry registry, String name, CircuitBreakerSettings settings) {
        CircuitBreaker breaker = registry.circuitBreaker(name, settings.toConfig());
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker '{}': {}", name, event.getStateTransition()));
        return breaker;
    }
}
