package com.nodotextil.trazatex.organizationaccess.domain;


public class InvalidCredentialsException extends OrganizationAccessException {

	public InvalidCredentialsException() {
		super("Invalid credentials");
	}
}
