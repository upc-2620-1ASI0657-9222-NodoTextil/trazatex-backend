package com.nodotextil.trazatex.organizationaccess.application;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryLicenseRepository;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapLicenseUseCaseTest {

	private static final String PASSWORD = "owner-password-123";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final InMemoryLicenseRepository licenses = new InMemoryLicenseRepository();
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final BootstrapLicenseUseCase useCase = new BootstrapLicenseUseCase(licenses, users,
			encoder, FixedClocks.at(NOW));

	@Test
	void createsTheLicenseAndTheLicenseOwner() {
		useCase.execute("LOCAL-DEV", 5, 20, "Owner@Example.com", PASSWORD);

		var license = licenses.findFirst().orElseThrow();
		assertThat(license.code()).isEqualTo("LOCAL-DEV");
		assertThat(license.maxCompanies()).isEqualTo(5);
		assertThat(license.maxUsersPerCompany()).isEqualTo(20);
		OrganizationUser owner = users.findByEmail("owner@example.com").orElseThrow();
		assertThat(owner.role()).isEqualTo(Role.LICENSE_OWNER);
		assertThat(owner.companyId()).isNull();
		assertThat(owner.isActive()).isTrue();
		assertThat(owner.passwordHash()).isNotEqualTo(PASSWORD);
		assertThat(encoder.matches(PASSWORD, owner.passwordHash())).isTrue();
	}

	@Test
	void runningItAgainChangesNothing() {
		useCase.execute("LOCAL-DEV", 5, 20, "owner@example.com", PASSWORD);
		var license = licenses.findFirst().orElseThrow();
		var owner = users.findByEmail("owner@example.com").orElseThrow();

		useCase.execute("OTHER", 9, 9, "owner@example.com", "another-password-123");

		assertThat(licenses.findFirst().orElseThrow().id()).isEqualTo(license.id());
		assertThat(licenses.findFirst().orElseThrow().code()).isEqualTo("LOCAL-DEV");
		assertThat(users.findByEmail("owner@example.com").orElseThrow().passwordHash())
				.isEqualTo(owner.passwordHash());
	}

	@Test
	void requiresAnEmailAndALongEnoughPassword() {
		assertThatThrownBy(() -> useCase.execute("LOCAL-DEV", 5, 20, "", PASSWORD))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> useCase.execute("LOCAL-DEV", 5, 20, "owner@example.com", "short"))
				.isInstanceOf(OrganizationValidationException.class)
				.satisfies(error -> assertThat(error.getMessage()).doesNotContain("short"));
		assertThat(licenses.findFirst()).isEmpty();
	}
}
