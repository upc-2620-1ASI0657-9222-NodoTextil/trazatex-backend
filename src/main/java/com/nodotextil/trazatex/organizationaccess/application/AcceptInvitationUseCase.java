package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.CompromisedPasswordPort;
import com.nodotextil.trazatex.organizationaccess.application.port.InvitationRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AcceptInvitationUseCase {

	private final InvitationRepository invitations;
	private final OrganizationUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final CompromisedPasswordPort compromisedPasswords;
	private final Clock clock;

	@Autowired
	public AcceptInvitationUseCase(InvitationRepository invitations,
			OrganizationUserRepository users, PasswordEncoder passwordEncoder,
			CompromisedPasswordPort compromisedPasswords) {
		this(invitations, users, passwordEncoder, compromisedPasswords, Clock.systemUTC());
	}

	AcceptInvitationUseCase(InvitationRepository invitations, OrganizationUserRepository users,
			PasswordEncoder passwordEncoder, CompromisedPasswordPort compromisedPasswords,
			Clock clock) {
		this.invitations = invitations;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.compromisedPasswords = compromisedPasswords;
		this.clock = clock;
	}

	@Transactional
	public OrganizationUser execute(String token, String password, String passwordConfirmation,
			String firstName, String lastName) {
		Invitation invitation = invitations.findByToken(token)
				.orElseThrow(() -> new OrganizationNotFoundException("Invitation not found"));
		LocalDateTime now = LocalDateTime.now(clock);
		if (!invitation.isUsableAt(now)) {
			throw new OrganizationConflictException("The invitation is "
					+ invitation.effectiveStatusAt(now) + " and cannot be used");
		}

		validatePassword(password, passwordConfirmation);
		String resolvedFirstName = firstNonBlank(firstName, invitation.firstName());
		String resolvedLastName = firstNonBlank(lastName, invitation.lastName());
		if (resolvedFirstName == null) {
			throw new OrganizationValidationException("First name is required",
					Map.of("firstName", "is required"));
		}
		if (resolvedLastName == null) {
			throw new OrganizationValidationException("Last name is required",
					Map.of("lastName", "is required"));
		}
		if (users.findByEmail(invitation.email()).isPresent()) {
			throw new OrganizationConflictException("A user with this email already exists");
		}
		if (compromisedPasswords.isCompromised(password)) {
			throw new OrganizationValidationException(
					"The password has appeared in a known data breach; choose another one",
					Map.of("password", "Has appeared in a known data breach"));
		}

		OrganizationUser user = users.save(OrganizationUser.create(invitation.email(),
				resolvedFirstName, resolvedLastName, invitation.jobTitle(),
				passwordEncoder.encode(password), invitation.role(), invitation.companyId(), now));
		invitations.save(invitation.accept());
		return user;
	}

	private static void validatePassword(String password, String passwordConfirmation) {
		if (password == null || password.isBlank()) {
			throw new OrganizationValidationException("The password is required",
					Map.of("password", "is required"));
		}
		if (passwordConfirmation == null || passwordConfirmation.isBlank()) {
			throw new OrganizationValidationException("The password confirmation is required",
					Map.of("passwordConfirmation", "is required"));
		}
		if (!password.equals(passwordConfirmation)) {
			throw new OrganizationValidationException(
					"The password and its confirmation do not match",
					Map.of("passwordConfirmation", "Does not match the password"));
		}
		if (password.length() < PasswordPolicy.MIN_LENGTH) {
			throw new OrganizationValidationException(
					"The password must have at least " + PasswordPolicy.MIN_LENGTH + " characters",
					Map.of("password", "Must have at least " + PasswordPolicy.MIN_LENGTH
							+ " characters"));
		}
	}

	private static String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred.trim();
		}
		return fallback == null || fallback.isBlank() ? null : fallback.trim();
	}
}
