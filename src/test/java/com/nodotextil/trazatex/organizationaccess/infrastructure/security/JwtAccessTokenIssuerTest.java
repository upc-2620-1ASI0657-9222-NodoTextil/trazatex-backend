package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer.IssuedAccessToken;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtAccessTokenIssuerTest {

	private static final Instant NOW = Instant.now();
	private static final SecretKey KEY = new SecretKeySpec(
			"a-test-secret-with-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8),
			"HmacSHA256");

	private final JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(KEY, "trazatex",
			Clock.fixed(NOW, ZoneOffset.UTC));
	private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY)
			.macAlgorithm(MacAlgorithm.HS256).build();

	private OrganizationUser user(Role role, UUID companyId) {
		return OrganizationUser.create("u@example.com", "Ana", "Lopez", null, "hash", role,
				companyId, LocalDateTime.of(2026, 10, 1, 9, 0));
	}

	@Test
	void companyUserTokenCarriesUserRoleAndCompany() {
		UUID companyId = UUID.randomUUID();
		OrganizationUser operator = user(Role.OPERATOR, companyId);

		IssuedAccessToken token = issuer.issue(operator);
		Jwt jwt = decoder.decode(token.value());

		assertThat(jwt.getClaimAsString("userId")).isEqualTo(operator.id().toString());
		assertThat(jwt.getClaimAsString("role")).isEqualTo("OPERATOR");
		assertThat(jwt.getClaimAsString("companyId")).isEqualTo(companyId.toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("trazatex");
		assertThat(jwt.getSubject()).isEqualTo(operator.id().toString());
	}

	@Test
	void licenseOwnerTokenHasNoCompanyClaim() {
		Jwt jwt = decoder.decode(issuer.issue(user(Role.LICENSE_OWNER, null)).value());

		assertThat(jwt.getClaimAsString("role")).isEqualTo("LICENSE_OWNER");
		assertThat(jwt.hasClaim("companyId")).isFalse();
	}

	@Test
	void tokenLivesOneHour() {
		IssuedAccessToken token = issuer.issue(user(Role.COMPANY_ADMIN, UUID.randomUUID()));
		Jwt jwt = decoder.decode(token.value());

		assertThat(token.expiresInSeconds()).isEqualTo(3600);
		assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond())
				.isEqualTo(3600);
	}
}
