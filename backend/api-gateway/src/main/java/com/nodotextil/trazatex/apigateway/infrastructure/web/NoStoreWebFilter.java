package com.nodotextil.trazatex.apigateway.infrastructure.web;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
class NoStoreWebFilter implements WebFilter, Ordered {

    static final String PREFIX = "/api/traceability";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path != null && (path.equals(PREFIX) || path.startsWith(PREFIX + "/"))) {
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
                return Mono.empty();
            });
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
