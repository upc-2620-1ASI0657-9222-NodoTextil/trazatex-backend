package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("trazatex.chequea")
public record ChequeaProperties(
        @DefaultValue("https://api.chequea.pe") String baseUrl,
        @DefaultValue("") String token,
        @DefaultValue("PT3S") Duration timeout,
        @DefaultValue("PT24H") Duration cacheTtl,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {

    @Override
    public String toString() {
        return "ChequeaProperties[baseUrl=" + baseUrl + ", token=" + (token == null || token.isBlank() ? "(vacío)" : "****")
                + ", timeout=" + timeout + ", cacheTtl=" + cacheTtl + "]";
    }
}
