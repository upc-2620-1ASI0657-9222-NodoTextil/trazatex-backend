package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nodotextil.trazatex.apigateway.domain.exception.AuthenticationFailedException;
import com.nodotextil.trazatex.apigateway.domain.exception.RateLimitExceededException;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Optional;

public interface AuthFailureThrottle {

    Optional<RateLimitExceededException> onFailure(ServerHttpRequest request, AuthenticationFailedException failure);
}
