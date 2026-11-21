package com.nodotextil.trazatex.production.infrastructure.external;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

public record CircuitBreakerSettings(
        @DefaultValue("10") int slidingWindowSize,
        @DefaultValue("5") int minimumCalls,
        @DefaultValue("50") float failureRateThreshold,
        @DefaultValue("PT30S") Duration waitInOpenState,
        @DefaultValue("2") int halfOpenCalls) {

    public CircuitBreakerConfig toConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitInOpenState)
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .build();
    }
}
