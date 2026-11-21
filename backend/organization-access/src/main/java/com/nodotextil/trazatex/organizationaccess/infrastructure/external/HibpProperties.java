package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("trazatex.hibp")
public record HibpProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.pwnedpasswords.com") String baseUrl,
        @DefaultValue("PT2S") Duration timeout,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {
}
