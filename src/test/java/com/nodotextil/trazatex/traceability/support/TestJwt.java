package com.nodotextil.trazatex.traceability.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor companyUser(UUID userId, UUID companyId) {
        return jwt().jwt(token -> token
                        .claim("userId", userId.toString())
                        .claim("companyId", companyId.toString())
                        .claim("role", "OPERATOR"))
                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    public static JwtRequestPostProcessor licenseOwner(UUID userId) {
        return jwt().jwt(token -> token
                        .claim("userId", userId.toString())
                        .claim("role", "LICENSE_OWNER"))
                .authorities(new SimpleGrantedAuthority("ROLE_LICENSE_OWNER"));
    }
}
