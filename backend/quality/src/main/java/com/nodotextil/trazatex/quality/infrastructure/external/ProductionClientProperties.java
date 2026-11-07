package com.nodotextil.trazatex.quality.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * El cliente del comando interno de Production (ADR-009): timeout de lectura de 2 s, circuit breaker y un retry acotado de 2 intentos
 * en total. {@code baseUrl} es la dirección de Production en la red interna (nunca el Gateway, que responde 404 a {@code /internal/**}).
 */
@ConfigurationProperties(prefix = "trazatex.production")
public record ProductionClientProperties(
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue Retry retry,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {

    /** {@code maxAttempts} cuenta el primer intento: 2 = una repetición. */
    public record Retry(@DefaultValue("2") int maxAttempts, @DefaultValue("PT0.3S") Duration waitDuration) {
    }
}
