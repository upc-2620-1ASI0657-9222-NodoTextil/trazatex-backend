package com.nodotextil.trazatex.organizationaccess.domain;

/** A rule of the module forbids the operation for the current user (HTTP 403). */
public class OrganizationAccessDeniedException extends OrganizationAccessException {

	public OrganizationAccessDeniedException(String message) {
		super(message);
	}
}
