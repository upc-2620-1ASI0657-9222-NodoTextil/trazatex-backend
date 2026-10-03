package com.nodotextil.trazatex.organizationaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LicenseAndCompanyTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	@Test
	void licenseLimitsAreInclusiveUpperBounds() {
		License license = License.create("LIC-1", null, 2, 3, NOW);

		assertThat(license.allowsAnotherCompany(1)).isTrue();
		assertThat(license.allowsAnotherCompany(2)).isFalse();
		assertThat(license.allowsAnotherUser(2)).isTrue();
		assertThat(license.allowsAnotherUser(3)).isFalse();
	}

	@Test
	void licenseLimitsMustBePositive() {
		assertThatThrownBy(() -> License.create("LIC-1", null, 0, 3, NOW))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> License.create("LIC-1", null, 2, 0, NOW))
				.isInstanceOf(OrganizationValidationException.class);
	}

	@Test
	void newCompanyIsActiveAndKeepsItsData() {
		Company company = Company.create(UUID.randomUUID(), "20123456789", " Textiles SAC ",
				Set.of("SPINNING"), NOW);

		assertThat(company.isActive()).isTrue();
		assertThat(company.status()).isEqualTo(CompanyStatus.ACTIVE);
		assertThat(company.legalName()).isEqualTo("Textiles SAC");
		assertThat(company.activities()).containsExactly("SPINNING");
	}

	@Test
	void rucMustHaveElevenDigits() {
		UUID licenseId = UUID.randomUUID();
		assertThatThrownBy(() -> Company.create(licenseId, "2012345678", "X", Set.of(), NOW))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> Company.create(licenseId, "2012345678A", "X", Set.of(), NOW))
				.isInstanceOf(OrganizationValidationException.class);
	}
}
