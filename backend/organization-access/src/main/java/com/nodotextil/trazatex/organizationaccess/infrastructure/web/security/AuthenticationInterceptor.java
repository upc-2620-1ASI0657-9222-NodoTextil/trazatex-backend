package com.nodotextil.trazatex.organizationaccess.infrastructure.web.security;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.UUID;

/**
 * Identidad y rol a partir de {@code X-User-Id}, {@code X-Company-Id} y {@code X-User-Role} (ADR-005): el Gateway valida el
 * JWT, descarta las cabeceras {@code X-*} que traiga el cliente e inyecta estas; el servicio solo es alcanzable por la red interna.
 * Un endpoint sin {@link PublicEndpoint} exige identidad; con {@link RequiresRole}, además uno de los roles.
 */
class AuthenticationInterceptor implements HandlerInterceptor {

    static final String ACTOR_ATTRIBUTE = AuthenticationInterceptor.class.getName() + ".ACTOR";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || method.hasMethodAnnotation(PublicEndpoint.class)) {
            return true;
        }
        Actor actor = parseActor(request);
        RequiresRole required = method.getMethodAnnotation(RequiresRole.class);
        if (required != null && !Arrays.asList(required.value()).contains(actor.role())) {
            throw new ForbiddenException("No tienes permiso para realizar esta operación.");
        }
        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        return true;
    }

    private static Actor parseActor(HttpServletRequest request) {
        try {
            UUID userId = UUID.fromString(required(request, "X-User-Id"));
            Role role = Role.valueOf(required(request, "X-User-Role"));
            String company = request.getHeader("X-Company-Id");
            UUID companyId = company == null || company.isBlank() ? null : UUID.fromString(company);
            if (role.belongsToCompany() != (companyId != null)) {
                throw new AuthenticationRequiredException("La identidad de la petición es inconsistente.");
            }
            return new Actor(userId, companyId, role);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationRequiredException("La identidad de la petición no es válida.");
        }
    }

    private static String required(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new AuthenticationRequiredException("Falta la cabecera " + header + ".");
        }
        return value;
    }
}
