package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Entity-to-domain round trips; they need no database. */
class PersistenceMappingTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	@Test
	void licenseRoundTrip() {
		License license = License.create("LIC-1", "Perpetual", 3, 10, NOW);

		License restored = LicenseJpaEntity.from(license).toDomain();

		assertThat(restored).usingRecursiveComparison().isEqualTo(license);
	}

	@Test
	void companyRoundTrip() {
		Company company = Company.create(UUID.randomUUID(), "20123456789", "Textiles SAC",
				Set.of("SPINNING", "WEAVING"), NOW);

		Company restored = CompanyJpaEntity.from(company).toDomain();

		assertThat(restored).usingRecursiveComparison().isEqualTo(company);
	}

	@Test
	void userRoundTripKeepsJobTitleAndLoginState() {
		OrganizationUser user = new OrganizationUser(UUID.randomUUID(), "ana@example.com", "Ana",
				"Lopez", "Dyer", "hash", Role.OPERATOR, UUID.randomUUID(),
				UserStatus.ACTIVE, 2,
				NOW.plusMinutes(15), NOW);

		OrganizationUser restored = OrganizationUserJpaEntity.from(user).toDomain();

		assertThat(restored).usingRecursiveComparison().isEqualTo(user);
	}

	@Test
	void invitationRoundTripKeepsNamesAndJobTitle() {
		Invitation invitation = new Invitation(UUID.randomUUID(), "op@example.com",
				UUID.randomUUID(), Role.OPERATOR, "token", "Luis", "Perez", "Dyer", NOW,
				NOW.plusDays(7), UUID.randomUUID(), InvitationStatus.PENDING);

		Invitation restored = InvitationJpaEntity.from(invitation).toDomain();

		assertThat(restored).usingRecursiveComparison().isEqualTo(invitation);
	}

	@Test
	void adminRequestRoundTripKeepsNamesAndJobTitle() {
		AdminRequest request = new AdminRequest(UUID.randomUUID(), UUID.randomUUID(),
				"admin@example.com", "Rosa", "Diaz", "Manager", UUID.randomUUID(),
				AdminRequestStatus.PENDING, NOW, null, null);

		AdminRequest restored = AdminRequestJpaEntity.from(request).toDomain();

		assertThat(restored).usingRecursiveComparison().isEqualTo(request);
	}

	@Test
	void privacySettingRoundTrip() {
		CompanyPrivacySetting setting = new CompanyPrivacySetting(UUID.randomUUID(),
				PrivacyField.WASTE_REASON, PrivacyVisibility.SHARED);

		CompanyPrivacySetting restored = PrivacySettingJpaEntity.from(setting).toDomain();

		assertThat(restored).isEqualTo(setting);
	}
}
