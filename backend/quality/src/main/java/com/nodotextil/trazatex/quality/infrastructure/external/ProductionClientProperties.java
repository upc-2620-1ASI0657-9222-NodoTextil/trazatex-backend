package com.nodotextil.trazatex.quality.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "trazatex.production")
public record ProductionClientProperties(
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue Retry retry,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {

    public record Retry(@DefaultValue("2") int maxAttempts, @DefaultValue("PT0.3S") Duration waitDuration) {
    }
}
