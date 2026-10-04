package com.nodotextil.trazatex.organizationaccess.domain;

/**
 * The login failed (HTTP 401). The message is deliberately generic: it must not reveal whether
 * the email, the password, the account status or a lock caused the failure.
 */
public class InvalidCredentialsException extends OrganizationAccessException {

	public InvalidCredentialsException() {
		super("Invalid credentials");
	}
}
