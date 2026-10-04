package com.nodotextil.trazatex.organizationaccess.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Authenticated callers for {@code MockMvc} tests, with the claims the module reads. */
public final class TestJwt {

	private TestJwt() {
	}

	public static JwtRequestPostProcessor licenseOwner(UUID userId) {
		return jwt().jwt(token -> token.claim("userId", userId.toString())
				.claim("role", "LICENSE_OWNER")).authorities(
						new org.springframework.security.core.authority.SimpleGrantedAuthority(
								"ROLE_LICENSE_OWNER"));
	}

	public static JwtRequestPostProcessor companyAdmin(UUID userId, UUID companyId) {
		return companyUser(userId, companyId, "COMPANY_ADMIN");
	}

	public static JwtRequestPostProcessor operator(UUID userId, UUID companyId) {
		return companyUser(userId, companyId, "OPERATOR");
	}

	private static JwtRequestPostProcessor companyUser(UUID userId, UUID companyId, String role) {
		return jwt().jwt(token -> token.claim("userId", userId.toString())
				.claim("companyId", companyId.toString()).claim("role", role)).authorities(
						new org.springframework.security.core.authority.SimpleGrantedAuthority(
								"ROLE_" + role));
	}
}
