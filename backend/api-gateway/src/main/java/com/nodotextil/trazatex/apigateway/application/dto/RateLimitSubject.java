package com.nodotextil.trazatex.apigateway.application.dto;

import com.nodotextil.trazatex.apigateway.domain.model.AuthenticatedUser;

import java.util.Objects;

public sealed interface RateLimitSubject {

    record Login(String clientKey) implements RateLimitSubject {
        public Login {
            Objects.requireNonNull(clientKey, "clientKey");
        }
    }

    record Invitation(String clientKey) implements RateLimitSubject {
        public Invitation {
            Objects.requireNonNull(clientKey, "clientKey");
        }
    }

    record AuthenticationFailure(String clientKey) implements RateLimitSubject {
        public AuthenticationFailure {
            Objects.requireNonNull(clientKey, "clientKey");
        }
    }

    record Authenticated(AuthenticatedUser user) implements RateLimitSubject {
        public Authenticated {
            Objects.requireNonNull(user, "user");
        }
    }
}
