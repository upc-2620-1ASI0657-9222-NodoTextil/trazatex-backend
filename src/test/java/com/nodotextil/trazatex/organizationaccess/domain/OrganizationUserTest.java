package com.nodotextil.trazatex.organizationaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrganizationUserTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private OrganizationUser user(Role role, UUID companyId) {
		return OrganizationUser.create("  Ana@Example.COM ", "Ana", "Lopez", "Plant manager",
				"hash", role, companyId, NOW);
	}

	@Test
	void licenseOwnerHasNoCompany() {
		OrganizationUser owner = user(Role.LICENSE_OWNER, null);

		assertThat(owner.companyId()).isNull();
		assertThat(owner.isActive()).isTrue();
	}

	@Test
	void licenseOwnerCannotBelongToACompany() {
		assertThatThrownBy(() -> user(Role.LICENSE_OWNER, UUID.randomUUID()))
				.isInstanceOf(OrganizationValidationException.class)
				.hasMessageContaining("cannot belong to a company");
	}

	@Test
	void companyAdminAndOperatorRequireACompany() {
		assertThatThrownBy(() -> user(Role.COMPANY_ADMIN, null))
				.isInstanceOf(OrganizationValidationException.class)
				.hasMessageContaining("must belong to a company");
		assertThatThrownBy(() -> user(Role.OPERATOR, null))
				.isInstanceOf(OrganizationValidationException.class);
	}

	@Test
	void normalizesEmailAndKeepsOptionalJobTitle() {
		UUID companyId = UUID.randomUUID();
		OrganizationUser operator = OrganizationUser.create("Ana@Example.COM", "Ana", "Lopez",
				"  ", "hash", Role.OPERATOR, companyId, NOW);

		assertThat(operator.email()).isEqualTo("ana@example.com");
		assertThat(operator.jobTitle()).isNull();
		assertThat(operator.failedLoginAttempts()).isZero();
		assertThat(operator.lockedUntil()).isNull();
	}

	@Test
	void rejectsMissingNamesAndInvalidEmail() {
		UUID companyId = UUID.randomUUID();
		assertThatThrownBy(() -> OrganizationUser.create("ana@example.com", " ", "Lopez", null,
				"hash", Role.OPERATOR, companyId, NOW))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> OrganizationUser.create("not-an-email", "Ana", "Lopez", null,
				"hash", Role.OPERATOR, companyId, NOW))
				.isInstanceOf(OrganizationValidationException.class);
	}
}
