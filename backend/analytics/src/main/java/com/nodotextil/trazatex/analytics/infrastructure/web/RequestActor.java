package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Quién llama, según las cabeceras que inyecta el Gateway. Sin ellas, o inválidas, es {@code 401 AUTHENTICATION_REQUIRED}. */
public record RequestActor(UUID userId, UUID companyId, String role) {
    public static RequestActor from(HttpServletRequest request) {
        try {
            UUID user = UUID.fromString(request.getHeader("X-User-Id"));
            String role = request.getHeader("X-User-Role");
            if (!java.util.Set.of("LICENSE_OWNER", "COMPANY_ADMIN", "OPERATOR").contains(role)) throw new IllegalArgumentException();
            String company = request.getHeader("X-Company-Id");
            UUID companyId = company == null || company.isBlank() ? null : UUID.fromString(company);
            if ("LICENSE_OWNER".equals(role) == (companyId != null)) throw new IllegalArgumentException();
            return new RequestActor(user, companyId, role);
        } catch (RuntimeException e) {
            throw new AuthenticationRequiredException();
        }
    }

    public void requireCompanyAdmin() {
        if (!"COMPANY_ADMIN".equals(role) || companyId == null) {
            throw new ForbiddenException("Solo los administradores de empresa pueden consultar los indicadores.");
        }
    }
}
