package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nodotextil.trazatex.apigateway.domain.model.AuthenticatedUser;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class IdentityHeaders {

    static final String USER_ID = "X-User-Id";
    static final String USER_ROLE = "X-User-Role";
    static final String COMPANY_ID = "X-Company-Id";

    private static final List<String> RESERVED_PREFIXES = List.of("x-user-", "x-company-");

    private IdentityHeaders() {
    }

    static void strip(HttpHeaders headers) {
        new ArrayList<>(headers.headerNames()).stream().filter(IdentityHeaders::isReserved).forEach(headers::remove);
    }

    static void inject(HttpHeaders headers, AuthenticatedUser user) {
        headers.set(USER_ID, user.userId().toString());
        headers.set(USER_ROLE, user.role().name());
        if (user.companyId() != null) {
            headers.set(COMPANY_ID, user.companyId().toString());
        }
    }

    private static boolean isReserved(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace('_', '-');
        return RESERVED_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
