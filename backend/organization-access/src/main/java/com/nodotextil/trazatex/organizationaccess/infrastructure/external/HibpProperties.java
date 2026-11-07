package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Have I Been Pwned (ADR-007). {@code enabled} viene de {@code HIBP_ENABLED}; apagado, no se consulta ni se audita nada. */
@ConfigurationProperties("trazatex.hibp")
public record HibpProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.pwnedpasswords.com") String baseUrl,
        @DefaultValue("PT2S") Duration timeout,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {
}
