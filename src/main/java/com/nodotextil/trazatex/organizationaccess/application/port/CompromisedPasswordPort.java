package com.nodotextil.trazatex.organizationaccess.application.port;

/** Checks a password against known data breaches (Have I Been Pwned). */
public interface CompromisedPasswordPort {

	/**
	 * Whether the password appears in a known breach.
	 *
	 * @throws com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException
	 *         if the service cannot be queried
	 */
	boolean isCompromised(String password);
}
