package com.nodotextil.trazatex.organizationaccess.application.dto;

public record AcceptInvitationCommand(String firstName, String lastName, String password, String passwordConfirmation) {
}
