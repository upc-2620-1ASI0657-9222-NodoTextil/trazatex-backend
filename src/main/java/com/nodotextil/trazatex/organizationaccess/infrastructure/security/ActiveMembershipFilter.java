package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess.CompanyMembership;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On every authenticated request, confirms that the user of the token is still {@code ACTIVE} and
 * that the {@code role} and {@code companyId} claims still match their membership (RF-012).
 * Otherwise it answers 403.
 */
class ActiveMembershipFilter extends OncePerRequestFilter {

	private static final String FORBIDDEN_BODY = "{\"code\":\"FORBIDDEN\","
			+ "\"message\":\"The user is no longer allowed to access with this token\","
			+ "\"fieldErrors\":{}}";

	private final OrganizationAccess organizationAccess;

	ActiveMembershipFilter(OrganizationAccess organizationAccess) {
		this.organizationAccess = organizationAccess;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext()
				.getAuthentication() instanceof JwtAuthenticationToken authentication
				&& !stillMatchesMembership(authentication)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write(FORBIDDEN_BODY);
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean stillMatchesMembership(JwtAuthenticationToken authentication) {
		try {
			UUID userId = UUID.fromString(authentication.getToken().getClaimAsString("userId"));
			String companyClaim = authentication.getToken().getClaimAsString("companyId");
			UUID companyId = companyClaim == null ? null : UUID.fromString(companyClaim);
			String role = authentication.getToken().getClaimAsString("role");
			CompanyMembership membership = organizationAccess.findMembership(userId)
					.orElse(null);
			return membership != null && Objects.equals(membership.role(), role)
					&& Objects.equals(membership.companyId(), companyId);
		}
		catch (RuntimeException invalidClaims) {
			return false;
		}
	}
}
