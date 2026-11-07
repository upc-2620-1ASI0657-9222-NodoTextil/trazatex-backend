package com.nodotextil.trazatex.traceability.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ninguna respuesta de {@code /api/traceability/**} es cacheable: lo que se ve de una genealogía depende de la empresa que consulta (ADR-006),
 * y un caché compartido (un proxy, el ingress) podría entregarle a una empresa lo que se calculó para otra. Va en un filtro, antes de todo, y
 * no en cada controlador, para que alcance también a los errores (401, 403, 404, 400, 405, 500) y a las rutas que no existen.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class NoStoreFilter extends OncePerRequestFilter {

    static final String PREFIX = "/api/traceability";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PREFIX) || path.startsWith(PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
