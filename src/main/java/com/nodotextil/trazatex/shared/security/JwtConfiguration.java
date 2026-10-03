package com.nodotextil.trazatex.shared.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Provisional JWT setup: HS256 with a secret shared by the issuer and the resource server.
 *
 * <p>TODO: the final JWT decision (algorithm, key management, issuer) is still pending and will
 * be reviewed with the group. Keep every JWT-specific bean here so it can be replaced in one
 * place. The token claims ({@code userId}, {@code role}, {@code companyId}) are a published
 * contract and must not change.
 */
@Configuration
public class JwtConfiguration {

	private static final int MIN_SECRET_BYTES = 32;

	/**
	 * The default is for local development only. Every real environment must set
	 * {@code JWT_SECRET} (mapped to {@code app.security.jwt-secret}) with a private value.
	 */
	@Bean
	SecretKey jwtSecretKey(@Value("${app.security.jwt-secret:"
			+ "local-development-only-secret-change-it-before-deploy}") String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"app.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey,
			@Value("${app.security.jwt-issuer:trazatex}") String issuer) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer));
		decoder.setJwtValidator(validator);
		return decoder;
	}

	/** Turns the {@code role} claim into the authority {@code ROLE_<role>}. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
