package com.nodotextil.trazatex.organizationaccess.domain;

/** The requested resource does not exist (HTTP 404). */
public class OrganizationNotFoundException extends OrganizationAccessException {

	public OrganizationNotFoundException(String message) {
		super(message);
	}
}
