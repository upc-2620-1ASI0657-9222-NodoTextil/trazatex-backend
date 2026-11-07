package com.nodotextil.trazatex.apigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Validación de los JWT (ADR-005). El JWKS se lee de {@code JWKS_URI}, se guarda en caché y, si llega un {@code kid}
 * desconocido, se vuelve a pedir como máximo una vez cada {@code minReloadInterval} (30 s). Mientras no se haya podido cargar
 * nunca, un fallo se reintenta a los {@code failureRetryInterval} (5 s).
 */
@ConfigurationProperties("trazatex.jwt")
public record JwtProperties(
        String jwksUri,
        Duration cacheTtl,
        Duration minReloadInterval,
        Duration failureRetryInterval,
        Duration outageTolerance,
        Duration connectTimeout,
        Duration readTimeout,
        Duration clockSkew) {

    public JwtProperties {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("Falta JWKS_URI (trazatex.jwt.jwks-uri): la URL del JWKS de organization-access");
        }
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
        minReloadInterval = minReloadInterval == null ? Duration.ofSeconds(30) : minReloadInterval;
        failureRetryInterval = failureRetryInterval == null ? Duration.ofSeconds(5) : failureRetryInterval;
        outageTolerance = outageTolerance == null ? Duration.ofHours(8) : outageTolerance;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
    }
}
