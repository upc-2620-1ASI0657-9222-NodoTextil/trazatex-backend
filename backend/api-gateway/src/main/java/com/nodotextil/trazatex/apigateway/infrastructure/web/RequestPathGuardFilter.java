package com.nodotextil.trazatex.apigateway.infrastructure.web;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Primera barrera, antes del enrutamiento y de la autenticación, para toda petición:
 * <ul>
 *   <li>{@code /internal/**} no existe para el exterior: 404, igual que una ruta cualquiera (no se revela que existe, no se
 *       pide token). Es la red interna (ADR-009: comando Quality → Production). Vale para cualquier método, con o sin token, y
 *       aunque algún día una ruta del Gateway lo cubriera.</li>
 *   <li>Rutas con trucos de normalización se rechazan con 400: segmentos {@code .} o {@code ..} (también codificados),
 *       {@code %2F}, {@code \}, {@code ;}, {@code //} y bytes nulos. El Gateway reenvía la ruta tal cual y el servicio de destino
 *       (Tomcat) la normalizaría: {@code /api/batches/../../internal/...} o {@code /internal;x=1/...} llegarían a una ruta
 *       interna pasando por una ruta pública del Gateway.</li>
 * </ul>
 */
@Component
class RequestPathGuardFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            return chain.filter(exchange);
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00") || lower.contains("%3b")
                || lower.contains("\\") || lower.contains(";") || lower.contains("//")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        }
        String[] segments = rawPath.split("/", -1);
        for (int i = 1; i < segments.length; i++) {
            String segment;
            try {
                segment = UriUtils.decode(segments[i], StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST));
            }
            if (segment.equals(".") || segment.equals("..")) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST));
            }
        }
        if ("internal".equalsIgnoreCase(UriUtils.decode(segments.length > 1 ? segments[1] : "", StandardCharsets.UTF_8))) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // justo después de NoStoreWebFilter, que no decide nada: solo registra la cabecera de sus respuestas
    }
}
