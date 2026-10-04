package com.nodotextil.trazatex.organizationaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InvitationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private Invitation invitation(Role role, String firstName, String lastName, String jobTitle) {
		return new Invitation(UUID.randomUUID(), "Op@Example.com", UUID.randomUUID(), role,
				"token", firstName, lastName, jobTitle, NOW, NOW.plusDays(7), UUID.randomUUID(),
				InvitationStatus.PENDING);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "COMPANY_ADMIN", "OPERATOR" })
	void invitableRolesAreAccepted(Role role) {
		Invitation invitation = invitation(role, "Luis", "Perez", "Dyer");

		assertThat(invitation.role()).isEqualTo(role);
		assertThat(invitation.email()).isEqualTo("op@example.com");
		assertThat(invitation.firstName()).isEqualTo("Luis");
		assertThat(invitation.lastName()).isEqualTo("Perez");
		assertThat(invitation.jobTitle()).isEqualTo("Dyer");
	}

	@Test
	void licenseOwnerCannotBeInvited() {
		assertThatThrownBy(() -> invitation(Role.LICENSE_OWNER, null, null, null))
				.isInstanceOf(OrganizationValidationException.class)
				.hasMessageContaining("COMPANY_ADMIN or an OPERATOR");
	}

	@Test
	void namesAndJobTitleAreOptionalInTheModel() {
		Invitation invitation = invitation(Role.COMPANY_ADMIN, null, " ", null);

		assertThat(invitation.firstName()).isNull();
		assertThat(invitation.lastName()).isNull();
		assertThat(invitation.jobTitle()).isNull();
	}

	@Test
	void requiresCompanyAndCreator() {
		assertThatThrownBy(() -> new Invitation(UUID.randomUUID(), "a@b.co", null, Role.OPERATOR,
				"token", null, null, null, NOW, NOW.plusDays(7), UUID.randomUUID(),
				InvitationStatus.PENDING)).isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> new Invitation(UUID.randomUUID(), "a@b.co", UUID.randomUUID(),
				Role.OPERATOR, "token", null, null, null, NOW, NOW.plusDays(7), null,
				InvitationStatus.PENDING)).isInstanceOf(OrganizationValidationException.class);
	}
}
