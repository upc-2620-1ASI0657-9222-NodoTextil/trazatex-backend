package com.nodotextil.trazatex.organizationaccess.domain;

/** The request conflicts with the current state of a resource (HTTP 409). */
public class OrganizationConflictException extends OrganizationAccessException {

	public OrganizationConflictException(String message) {
		super(message);
	}
}
