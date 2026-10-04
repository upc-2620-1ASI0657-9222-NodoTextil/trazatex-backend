package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;


public interface AccessTokenIssuer {

	IssuedAccessToken issue(OrganizationUser user);

	record IssuedAccessToken(String value, long expiresInSeconds) {
	}
}
