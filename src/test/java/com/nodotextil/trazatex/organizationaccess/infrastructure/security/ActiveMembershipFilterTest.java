package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess.CompanyMembership;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ActiveMembershipFilterTest {

	private final OrganizationAccess access = mock(OrganizationAccess.class);
	private final ActiveMembershipFilter filter = new ActiveMembershipFilter(access);
	private final FilterChain chain = mock(FilterChain.class);
	private final MockHttpServletRequest request = new MockHttpServletRequest();
	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final UUID userId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private void authenticate(String user, String company, String role) {
		Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
		if (user != null) {
			builder.claim("userId", user);
		}
		if (company != null) {
			builder.claim("companyId", company);
		}
		builder.claim("role", role);
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
				builder.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
	}

	@Test
	void letsThroughAUserWhoseMembershipStillMatches() throws Exception {
		authenticate(userId.toString(), companyId.toString(), "OPERATOR");
		when(access.findMembership(userId)).thenReturn(
				Optional.of(new CompanyMembership(userId, companyId, "OPERATOR", true)));

		filter.doFilter(request, response, chain);

		Mockito.verify(chain).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void letsThroughALicenseOwnerWithoutCompany() throws Exception {
		authenticate(userId.toString(), null, "LICENSE_OWNER");
		when(access.findMembership(userId)).thenReturn(
				Optional.of(new CompanyMembership(userId, null, "LICENSE_OWNER", false)));

		filter.doFilter(request, response, chain);

		Mockito.verify(chain).doFilter(request, response);
	}

	@Test
	void rejectsAUserWhoIsNoLongerActive() throws Exception {
		authenticate(userId.toString(), companyId.toString(), "OPERATOR");
		when(access.findMembership(userId)).thenReturn(Optional.empty());

		filter.doFilter(request, response, chain);

		assertForbidden();
	}

	@Test
	void rejectsATokenWhoseRoleChanged() throws Exception {
		authenticate(userId.toString(), companyId.toString(), "COMPANY_ADMIN");
		when(access.findMembership(userId)).thenReturn(
				Optional.of(new CompanyMembership(userId, companyId, "OPERATOR", true)));

		filter.doFilter(request, response, chain);

		assertForbidden();
	}

	@Test
	void rejectsATokenWhoseCompanyChanged() throws Exception {
		authenticate(userId.toString(), companyId.toString(), "OPERATOR");
		when(access.findMembership(userId)).thenReturn(Optional.of(
				new CompanyMembership(userId, UUID.randomUUID(), "OPERATOR", true)));

		filter.doFilter(request, response, chain);

		assertForbidden();
	}

	@Test
	void rejectsATokenWithMalformedClaims() throws Exception {
		authenticate("not-a-uuid", companyId.toString(), "OPERATOR");

		filter.doFilter(request, response, chain);

		assertForbidden();
	}

	@Test
	void ignoresRequestsWithoutAJwtAuthentication() throws Exception {
		filter.doFilter(request, response, chain);

		Mockito.verify(chain).doFilter(request, response);
		Mockito.verifyNoInteractions(access);
	}

	private void assertForbidden() throws Exception {
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
		Mockito.verifyNoInteractions(chain);
	}
}
