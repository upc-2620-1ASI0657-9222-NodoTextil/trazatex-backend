package com.nodotextil.trazatex.organizationaccess.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Signs access tokens with HS256 and the shared secret.
 *
 * <p>TODO: provisional. The final JWT decision (algorithm, key management, who issues tokens) is
 * still pending; this class is the only place that creates tokens, so it is the one to replace.
 * The claims ({@code userId}, {@code role}, {@code companyId}) are a published contract and must
 * not change.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

	static final long TOKEN_LIFETIME_SECONDS = 3600;

	private final JwtEncoder encoder;
	private final String issuer;
	private final Clock clock;

	@Autowired
	JwtAccessTokenIssuer(SecretKey jwtSecretKey,
			@Value("${app.security.jwt-issuer:trazatex}") String issuer) {
		this(jwtSecretKey, issuer, Clock.systemUTC());
	}

	JwtAccessTokenIssuer(SecretKey jwtSecretKey, String issuer, Clock clock) {
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
		this.issuer = issuer;
		this.clock = clock;
	}

	@Override
	public IssuedAccessToken issue(OrganizationUser user) {
		Instant issuedAt = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer)
				.subject(user.id().toString()).issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS))
				.claim("userId", user.id().toString()).claim("role", user.role().name());
		if (user.companyId() != null) {
			claims.claim("companyId", user.companyId().toString());
		}
		String token = encoder.encode(JwtEncoderParameters
				.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
		return new IssuedAccessToken(token, TOKEN_LIFETIME_SECONDS);
	}
}
