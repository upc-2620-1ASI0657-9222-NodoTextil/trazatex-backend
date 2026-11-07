package com.nodotextil.trazatex.production.infrastructure.config;

import com.nodotextil.trazatex.production.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.production.application.usecase.EvidenceSettings;
import com.nodotextil.trazatex.production.domain.model.EvidenceRetryPolicy;
import com.nodotextil.trazatex.production.infrastructure.external.CloudinaryCredentials;
import com.nodotextil.trazatex.production.infrastructure.external.CloudinaryEvidenceStorageAdapter;
import com.nodotextil.trazatex.production.infrastructure.external.CloudinaryProperties;
import com.nodotextil.trazatex.production.infrastructure.external.ExternalHttpClients;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Evidencias: Cloudinary detrás de su puerto con timeout y circuit breaker (ADR-007) y los parámetros de la cola diferida. */
@Configuration
@EnableConfigurationProperties({CloudinaryProperties.class, EvidenceProperties.class})
class EvidenceConfig {

    private static final Logger log = LoggerFactory.getLogger(EvidenceConfig.class);

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    EvidenceStoragePort evidenceStorage(CloudinaryProperties properties, CircuitBreakerRegistry registry, ObjectMapper mapper, Clock clock) {
        CloudinaryCredentials credentials = CloudinaryCredentials.parse(properties.url());   // mal formada: no arranca, sin imprimir el secreto
        if (credentials == null) {
            log.warn("CLOUDINARY_URL no está configurada: las evidencias se aceptan y quedan PENDING en la cola local hasta que se configure");
        }
        CircuitBreaker breaker = registry.circuitBreaker("cloudinary", properties.circuitBreaker().toConfig());
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Circuit breaker 'cloudinary': {}", event.getStateTransition()));
        return new CloudinaryEvidenceStorageAdapter(
                ExternalHttpClients.restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout()),
                breaker, credentials, mapper, clock);
    }

    @Bean
    EvidenceSettings evidenceSettings(EvidenceProperties properties) {
        EvidenceProperties.UploadQueue queue = properties.upload();
        return new EvidenceSettings(properties.maxFiles(), properties.maxFileSize().toBytes(), queue.claimsPerRun(), queue.lease(),
                queue.notAvailableDelay(), new EvidenceRetryPolicy(queue.maxAttempts(), queue.initialDelay(), queue.multiplier(), queue.maxDelay()));
    }
}
