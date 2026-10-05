package com.nodotextil.trazatex.shared.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess.CompanyMembership;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.ActiveMembershipSecurityConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.ProbeController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class, ActiveMembershipSecurityConfiguration.class,
		SecurityConfigTest.ProbeController.class })
@TestPropertySource(properties = "app.security.jwt-issuer=trazatex")
class SecurityConfigTest {

	static final String SECRET = "a-test-secret-with-at-least-32-bytes!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

	@MockitoBean
	private OrganizationAccess organizationAccess;

	private final UUID userId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();

	@RestController
	static class ProbeController {

		@PostMapping("/api/auth/login")
		String login() {
			return "login";
		}

		@GetMapping("/api/invitations/{token}")
		String invitation() {
			return "invitation";
		}

		@PostMapping("/api/invitations/{token}/accept")
		String accept() {
			return "accepted";
		}

		@PostMapping("/api/invitations/{id}/cancel")
		String cancel() {
			return "cancelled";
		}

		@GetMapping("/actuator/health")
		String health() {
			return "UP";
		}

		@GetMapping("/api/probe")
		String probe() {
			return "ok";
		}

		@GetMapping("/api/probe/admin")
		@PreAuthorize("hasRole('COMPANY_ADMIN')")
		String adminOnly() {
			return "admin";
		}
	}	private String token(String role, UUID company, String issuer, Instant expiresAt) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer)
				.subject(userId.toString()).issuedAt(Instant.now().minusSeconds(900))
				.expiresAt(expiresAt).claim("userId", userId.toString()).claim("role", role);
		if (company != null) {
			claims.claim("companyId", company.toString());
		}
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
	}

	private String validToken(String role) {
		return token(role, companyId, "trazatex", Instant.now().plusSeconds(600));
	}

	private void membershipIs(String role) {
		when(organizationAccess.findMembership(userId)).thenReturn(
				Optional.of(new CompanyMembership(userId, companyId, role, true)));
	}

	@Test
	void publicRoutesNeedNoToken() throws Exception {
		mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
		mockMvc.perform(get("/api/invitations/some-token_value-123")).andExpect(status().isOk());
		mockMvc.perform(post("/api/invitations/some-token_value-123/accept"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void everythingElseRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/invitations/" + UUID.randomUUID() + "/cancel"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aValidTokenOfAnActiveMemberIsAccepted() throws Exception {
		membershipIs("OPERATOR");

		mockMvc.perform(get("/api/probe").header("Authorization",
				"Bearer " + validToken("OPERATOR"))).andExpect(status().isOk())
				.andExpect(content().string("ok"));
	}

	@Test
	void rolesAreDeclaredPerControllerWithPreAuthorize() throws Exception {
		membershipIs("OPERATOR");
		mockMvc.perform(get("/api/probe/admin").header("Authorization",
				"Bearer " + validToken("OPERATOR"))).andExpect(status().isForbidden());

		membershipIs("COMPANY_ADMIN");
		mockMvc.perform(get("/api/probe/admin").header("Authorization",
				"Bearer " + validToken("COMPANY_ADMIN"))).andExpect(status().isOk());
	}

	@Test
	void aTokenWhoseMembershipNoLongerMatchesIsForbidden() throws Exception {
		membershipIs("OPERATOR");
		mockMvc.perform(get("/api/probe").header("Authorization",
				"Bearer " + validToken("COMPANY_ADMIN"))).andExpect(status().isForbidden());

		when(organizationAccess.findMembership(userId)).thenReturn(Optional.empty());
		mockMvc.perform(get("/api/probe").header("Authorization",
				"Bearer " + validToken("OPERATOR"))).andExpect(status().isForbidden());
	}

	@Test
	void expiredTokensAndTokensOfAnotherIssuerAreRejected() throws Exception {
		membershipIs("OPERATOR");
		String expired = token("OPERATOR", companyId, "trazatex", Instant.now().minusSeconds(300));
		String foreign = token("OPERATOR", companyId, "someone-else",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + foreign))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/probe").header("Authorization", "Bearer garbage"))
				.andExpect(status().isUnauthorized());
	}
}

