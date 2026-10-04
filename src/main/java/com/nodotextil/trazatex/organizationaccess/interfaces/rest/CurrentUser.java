package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller's identity from the JWT claims (the membership filter keeps them honest). */
final class CurrentUser {

	private CurrentUser() {
	}

	static UUID id(Jwt jwt) {
		return UUID.fromString(jwt.getClaimAsString("userId"));
	}

	/** {@code null} for a {@code LICENSE_OWNER}. */
	static UUID companyId(Jwt jwt) {
		String companyId = jwt.getClaimAsString("companyId");
		return companyId == null ? null : UUID.fromString(companyId);
	}
}
