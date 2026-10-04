package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;

/** Issues the access token a user presents on later requests. */
public interface AccessTokenIssuer {

	IssuedAccessToken issue(OrganizationUser user);

	record IssuedAccessToken(String value, long expiresInSeconds) {
	}
}
