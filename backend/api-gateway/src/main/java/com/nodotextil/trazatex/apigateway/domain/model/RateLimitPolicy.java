package com.nodotextil.trazatex.apigateway.domain.model;

import java.util.Objects;

/**
 * Los cuatro límites. En las rutas autenticadas {@code user} y {@code company} deben cumplirse <b>a la vez</b> (el informe pide
 * límite "por usuario y empresa"); el Dueño de Licencia, sin empresa, solo usa el de usuario.
 */
public record RateLimitPolicy(RateLimit login, RateLimit invitation, RateLimit user, RateLimit company, RateLimit authFailure) {

    public RateLimitPolicy {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(invitation, "invitation");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(authFailure, "authFailure");
    }
}
