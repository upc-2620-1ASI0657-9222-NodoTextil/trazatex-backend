package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Chequea.pe (ADR-012). {@code token} viene de {@code CHEQUEA_API_TOKEN}; sin él no se acepta ningún RUC que no esté en caché.
 * {@code cacheTtl}: mientras una validación en caché es más reciente que esto no se vuelve a llamar a la API; una más antigua
 * se vuelve a consultar y solo se usa si la API no responde.
 */
@ConfigurationProperties("trazatex.chequea")
public record ChequeaProperties(
        @DefaultValue("https://api.chequea.pe") String baseUrl,
        @DefaultValue("") String token,
        @DefaultValue("PT3S") Duration timeout,
        @DefaultValue("PT24H") Duration cacheTtl,
        @DefaultValue CircuitBreakerSettings circuitBreaker) {

    /** El token no se imprime nunca. */
    @Override
    public String toString() {
        return "ChequeaProperties[baseUrl=" + baseUrl + ", token=" + (token == null || token.isBlank() ? "(vacío)" : "****")
                + ", timeout=" + timeout + ", cacheTtl=" + cacheTtl + "]";
    }
}
