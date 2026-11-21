package com.nodotextil.trazatex.apigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("trazatex.rate-limit")
public record RateLimitProperties(
        Rule login,
        Rule invitation,
        Rule user,
        Rule company,
        Rule authFailure,
        Duration idleExpiry,
        Long maxEntries,
        Boolean trustForwardedFor,
        Integer trustedHops) {

    public record Rule(Long permits, Duration window) {

        static Rule resolve(String name, Rule configured, long defaultPermits) {
            long permits = configured == null || configured.permits() == null ? defaultPermits : configured.permits();
            Duration window = configured == null || configured.window() == null ? Duration.ofMinutes(1) : configured.window();
            if (permits < 1) {
                throw new IllegalStateException("trazatex.rate-limit." + name + ".permits debe ser al menos 1");
            }
            if (window.compareTo(Duration.ofSeconds(1)) < 0) {
                throw new IllegalStateException("trazatex.rate-limit." + name + ".window debe ser al menos 1 s");
            }
            return new Rule(permits, window);
        }
    }

    public RateLimitProperties {
        login = Rule.resolve("login", login, 60);
        invitation = Rule.resolve("invitation", invitation, 30);
        user = Rule.resolve("user", user, 120);
        company = Rule.resolve("company", company, 600);
        authFailure = Rule.resolve("auth-failure", authFailure, 300);
        idleExpiry = idleExpiry == null ? Duration.ofMinutes(10) : idleExpiry;
        maxEntries = maxEntries == null ? 100_000L : maxEntries;
        trustForwardedFor = trustForwardedFor != null && trustForwardedFor;
        trustedHops = trustedHops == null ? 1 : trustedHops;
        if (maxEntries < 1) {
            throw new IllegalStateException("trazatex.rate-limit.max-entries debe ser al menos 1");
        }
        if (trustedHops < 1) {
            throw new IllegalStateException("trazatex.rate-limit.trusted-hops debe ser al menos 1");
        }
        Duration longest = java.util.stream.Stream.of(login, invitation, user, company, authFailure).map(Rule::window).max(Duration::compareTo).orElseThrow();
        if (idleExpiry.compareTo(longest) < 0) {
            throw new IllegalStateException("trazatex.rate-limit.idle-expiry (" + idleExpiry + ") no puede ser menor que la ventana más larga ("
                    + longest + "): un cubo que expira antes de rellenarse volvería a nacer lleno");
        }
    }
}
