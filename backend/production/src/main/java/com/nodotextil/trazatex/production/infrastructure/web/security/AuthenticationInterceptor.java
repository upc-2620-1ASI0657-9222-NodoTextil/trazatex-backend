package com.nodotextil.trazatex.production.infrastructure.web.security;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.UUID;

class AuthenticationInterceptor implements HandlerInterceptor {

    static final String ACTOR_ATTRIBUTE = AuthenticationInterceptor.class.getName() + ".ACTOR";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
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
