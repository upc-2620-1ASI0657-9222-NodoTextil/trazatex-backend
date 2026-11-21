package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nodotextil.trazatex.apigateway.application.port.in.AuthenticateUseCase;
import com.nodotextil.trazatex.apigateway.domain.exception.AuthenticationFailedException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class AuthenticationGatewayFilter implements GlobalFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    public static final String USER_ATTRIBUTE = "trazatex.authenticatedUser";

    private final PublicRoutes publicRoutes;
    private final AuthenticateUseCase authenticate;
    private final AuthFailureThrottle authFailureThrottle;

    public AuthenticationGatewayFilter(PublicRoutes publicRoutes, AuthenticateUseCase authenticate, AuthFailureThrottle authFailureThrottle) {
        this.publicRoutes = publicRoutes;
        this.authenticate = authenticate;
        this.authFailureThrottle = authFailureThrottle;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        ServerWebExchange clean = exchange.mutate().request(r -> r.headers(IdentityHeaders::strip)).build();
        if (publicRoutes.isPublic(clean.getRequest())) {
            return chain.filter(clean);
        }
        return authenticate.authenticate(authorization)
                .onErrorResume(AuthenticationFailedException.class, failure -> Mono.error(
                        authFailureThrottle.onFailure(clean.getRequest(), failure).<Throwable>map(limited -> limited).orElse(failure)))
                .flatMap(user -> {
                    clean.getAttributes().put(USER_ATTRIBUTE, user);
                    return chain.filter(clean.mutate().request(r -> r.headers(h -> IdentityHeaders.inject(h, user))).build());
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
