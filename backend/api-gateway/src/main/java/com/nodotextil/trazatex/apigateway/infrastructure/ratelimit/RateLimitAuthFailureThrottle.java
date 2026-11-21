package com.nodotextil.trazatex.apigateway.infrastructure.ratelimit;

import com.nodotextil.trazatex.apigateway.application.dto.RateLimitSubject;
import com.nodotextil.trazatex.apigateway.application.port.in.RateLimitUseCase;
import com.nodotextil.trazatex.apigateway.domain.exception.AuthenticationFailedException;
import com.nodotextil.trazatex.apigateway.domain.exception.RateLimitExceededException;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitDecision;
import com.nodotextil.trazatex.apigateway.infrastructure.security.AuthFailureThrottle;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Optional;

public class RateLimitAuthFailureThrottle implements AuthFailureThrottle {

    private final RateLimitUseCase rateLimit;
    private final ClientIpResolver clientIps;

    public RateLimitAuthFailureThrottle(RateLimitUseCase rateLimit, ClientIpResolver clientIps) {
        this.rateLimit = rateLimit;
        this.clientIps = clientIps;
    }

    @Override
    public Optional<RateLimitExceededException> onFailure(ServerHttpRequest request, AuthenticationFailedException failure) {
        if (failure.reason() != AuthenticationFailedException.Reason.MISSING_TOKEN
                && failure.reason() != AuthenticationFailedException.Reason.INVALID_TOKEN) {
            return Optional.empty();
        }
        RateLimitDecision decision = rateLimit.check(new RateLimitSubject.AuthenticationFailure(clientIps.resolve(request)));
        return decision.allowed() ? Optional.empty() : Optional.of(new RateLimitExceededException(decision.retryAfter()));
    }
}
