package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer;
import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer.IssuedAccessToken;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs a user in with email and password (RF-008). Only {@code ACTIVE} users can log in, three
 * consecutive failures lock the account for 15 minutes (RNF-06) and every failure raises the same
 * {@link InvalidCredentialsException}, so the caller cannot tell why the login failed.
 */
@Service
public class AuthenticateUserUseCase {

	private final OrganizationUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AccessTokenIssuer tokenIssuer;
	private final Clock clock;
	/** Lets an unknown email cost as much as a wrong password. */
	private final String unknownUserHash;

	@Autowired
	public AuthenticateUserUseCase(OrganizationUserRepository users,
			PasswordEncoder passwordEncoder, AccessTokenIssuer tokenIssuer) {
		this(users, passwordEncoder, tokenIssuer, Clock.systemUTC());
	}

	AuthenticateUserUseCase(OrganizationUserRepository users, PasswordEncoder passwordEncoder,
			AccessTokenIssuer tokenIssuer, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.clock = clock;
		this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public AuthenticationResult execute(String email, String password) {
		String rawPassword = password == null ? "" : password;
		Optional<OrganizationUser> found = users.findByEmail(email);
		if (found.isEmpty()) {
			passwordEncoder.matches(rawPassword, unknownUserHash);
			throw new InvalidCredentialsException();
		}
		OrganizationUser user = found.get();
		LocalDateTime now = LocalDateTime.now(clock);
		if (!user.isActive() || user.isLockedAt(now)) {
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
			users.save(user.withFailedLogin(now));
			throw new InvalidCredentialsException();
		}
		if (user.failedLoginAttempts() > 0 || user.lockedUntil() != null) {
			user = users.save(user.withSuccessfulLogin());
		}
		IssuedAccessToken token = tokenIssuer.issue(user);
		return new AuthenticationResult(token.value(), token.expiresInSeconds(), user.id(),
				user.companyId(), user.role().name());
	}

	/** {@code companyId} is {@code null} for a {@code LICENSE_OWNER}. */
	public record AuthenticationResult(String accessToken, long expiresInSeconds, UUID userId,
			UUID companyId, String role) {
	}
}
