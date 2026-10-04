package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.nodotextil.trazatex.organizationaccess.application.port.CompromisedPasswordPort;
import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryInvitationRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AcceptInvitationUseCaseTest {

	private static final String PASSWORD = "a-long-secure-password";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final InMemoryInvitationRepository invitations = new InMemoryInvitationRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final FixedClocks.Mutable clock = new FixedClocks.Mutable(NOW);
	private final List<String> checkedPasswords = new ArrayList<>();
	private boolean compromised = false;
	private boolean hibpDown = false;
	private final CompromisedPasswordPort hibp = password -> {
		checkedPasswords.add(password);
		if (hibpDown) {
			throw new ExternalServiceUnavailableException("HIBP validation is unavailable");
		}
		return compromised;
	};
	private final AcceptInvitationUseCase useCase = new AcceptInvitationUseCase(invitations, users,
			encoder, hibp, clock);

	private final UUID companyId = UUID.randomUUID();

	private Invitation invite(String first, String last, String job) {
		return invitations.save(Invitation.issue("new@example.com", companyId, Role.OPERATOR,
				"token-" + UUID.randomUUID(), first, last, job, UUID.randomUUID(), NOW));
	}

	@Test
	void createsTheActiveUserFromTheInvitation() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		OrganizationUser user = useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null);

		assertThat(user.email()).isEqualTo("new@example.com");
		assertThat(user.role()).isEqualTo(Role.OPERATOR);
		assertThat(user.companyId()).isEqualTo(companyId);
		assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.firstName()).isEqualTo("Luis");
		assertThat(user.lastName()).isEqualTo("Perez");
		assertThat(user.jobTitle()).isEqualTo("Dyer");
		assertThat(user.passwordHash()).isNotEqualTo(PASSWORD);
		assertThat(encoder.matches(PASSWORD, user.passwordHash())).isTrue();
		assertThat(users.findById(user.id())).isPresent();
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.ACCEPTED);
	}

	@Test
	void namesInTheRequestOverrideTheOnesInTheInvitationButTheJobTitleDoesNot() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		OrganizationUser user = useCase.execute(invitation.token(), PASSWORD, PASSWORD,
				"  Luis Alberto ", "Perez Soto");

		assertThat(user.firstName()).isEqualTo("Luis Alberto");
		assertThat(user.lastName()).isEqualTo("Perez Soto");
		assertThat(user.jobTitle()).isEqualTo("Dyer");
	}

	@Test
	void namesCanComeOnlyFromTheRequestWhenTheInvitationHasNone() {
		Invitation invitation = invite(null, null, null);

		OrganizationUser user = useCase.execute(invitation.token(), PASSWORD, PASSWORD, "Rosa",
				"Diaz");

		assertThat(user.firstName()).isEqualTo("Rosa");
		assertThat(user.lastName()).isEqualTo("Diaz");
		assertThat(user.jobTitle()).isNull();
	}

	@Test
	void failsWhenNoSourceHasTheNames() {
		Invitation invitation = invite(null, null, null);

		OrganizationValidationException missingFirst = catchThrowableOfType(
				OrganizationValidationException.class,
				() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null, "Diaz"));
		OrganizationValidationException missingLast = catchThrowableOfType(
				OrganizationValidationException.class,
				() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, "Rosa", " "));

		assertThat(missingFirst.fieldErrors()).containsKey("firstName");
		assertThat(missingLast.fieldErrors()).containsKey("lastName");
		assertThat(users.findByEmail("new@example.com")).isEmpty();
	}

	@Test
	void theConfirmationMustMatch() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class, () -> useCase.execute(invitation.token(),
						PASSWORD, PASSWORD + "x", null, null));

		assertThat(error.fieldErrors()).containsKey("passwordConfirmation");
		assertThat(users.findByEmail("new@example.com")).isEmpty();
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void theConfirmationIsRequired() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		for (String missing : new String[] { null, "", "  " }) {
			OrganizationValidationException error = catchThrowableOfType(
					OrganizationValidationException.class, () -> useCase.execute(
							invitation.token(), PASSWORD, missing, null, null));
			assertThat(error.fieldErrors()).containsKey("passwordConfirmation");
		}
	}

	@Test
	void thePasswordIsRequiredAndNeedsAtLeastTwelveCharacters() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		assertThatThrownBy(() -> useCase.execute(invitation.token(), null, null, null, null))
				.isInstanceOf(OrganizationValidationException.class);
		OrganizationValidationException tooShort = catchThrowableOfType(
				OrganizationValidationException.class, () -> useCase.execute(invitation.token(),
						"short-pass1", "short-pass1", null, null));

		assertThat(tooShort.fieldErrors()).containsKey("password");
		assertThat(useCase.execute(invitation.token(), "exactly12chr", "exactly12chr", null,
				null)).isNotNull();
	}

	@Test
	void theBreachCheckRunsOnlyAfterTheConfirmationIsValid() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, "different",
				null, null)).isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> useCase.execute(invitation.token(), "short", "short", null,
				null)).isInstanceOf(OrganizationValidationException.class);
		assertThat(checkedPasswords).isEmpty();

		useCase.execute(invitation.token(), PASSWORD, PASSWORD, null, null);
		assertThat(checkedPasswords).containsExactly(PASSWORD);
	}

	@Test
	void aCompromisedPasswordIsRejected() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");
		compromised = true;

		OrganizationValidationException error = catchThrowableOfType(
				OrganizationValidationException.class, () -> useCase.execute(invitation.token(),
						PASSWORD, PASSWORD, null, null));

		assertThat(error.fieldErrors()).containsKey("password");
		assertThat(error.getMessage()).doesNotContain(PASSWORD);
		assertThat(users.findByEmail("new@example.com")).isEmpty();
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void anHibpOutageBlocksTheAccountCreation() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");
		hibpDown = true;

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(users.findByEmail("new@example.com")).isEmpty();
	}

	@Test
	void anUnknownTokenIsNotFound() {
		assertThatThrownBy(() -> useCase.execute("nope", PASSWORD, PASSWORD, null, null))
				.isInstanceOf(OrganizationNotFoundException.class);
		assertThat(checkedPasswords).isEmpty();
	}

	@Test
	void anExpiredInvitationCannotBeUsed() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");
		clock.advanceDays(7);

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("EXPIRED");
		assertThat(users.findByEmail("new@example.com")).isEmpty();
	}

	@Test
	void anInvitationExpiredByTheJobCannotBeUsed() {
		Invitation invitation = invitations.save(invite("Luis", "Perez", "Dyer").expire());

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void aCancelledInvitationCannotBeUsed() {
		Invitation invitation = invitations.save(invite("Luis", "Perez", "Dyer").cancel());

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("CANCELLED");
	}

	@Test
	void anInvitationCannotBeUsedTwice() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");
		useCase.execute(invitation.token(), PASSWORD, PASSWORD, null, null);

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(OrganizationConflictException.class)
				.hasMessageContaining("ACCEPTED");
	}

	@Test
	void anEmailThatAlreadyHasAnAccountConflicts() {
		Invitation invitation = invite("Luis", "Perez", "Dyer");
		users.save(TestData.user("new@example.com", Role.OPERATOR, UUID.randomUUID()));

		assertThatThrownBy(() -> useCase.execute(invitation.token(), PASSWORD, PASSWORD, null,
				null)).isInstanceOf(OrganizationConflictException.class);
		assertThat(invitations.findById(invitation.id()).orElseThrow().status())
				.isEqualTo(InvitationStatus.PENDING);
	}
}
