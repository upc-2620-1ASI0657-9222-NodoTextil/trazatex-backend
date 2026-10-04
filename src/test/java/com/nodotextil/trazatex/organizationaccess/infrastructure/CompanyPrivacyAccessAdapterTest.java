package com.nodotextil.trazatex.organizationaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryPrivacySettingRepository;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CompanyPrivacyAccessAdapterTest {

	private final InMemoryPrivacySettingRepository settings = new InMemoryPrivacySettingRepository();
	private final CompanyPrivacyAccessAdapter access = new CompanyPrivacyAccessAdapter(settings);
	private final UUID companyId = UUID.randomUUID();

	@Test
	void alwaysSharedFieldsAreSharedAndAlwaysPrivateFieldsArePrivate() {
		for (PrivacyField field : PrivacyField.inCategory(PrivacyCategory.ALWAYS_SHARED)) {
			assertThat(access.visibilityOf(companyId, field)).isEqualTo(PrivacyVisibility.SHARED);
		}
		for (PrivacyField field : PrivacyField.inCategory(PrivacyCategory.ALWAYS_PRIVATE)) {
			assertThat(access.visibilityOf(companyId, field)).isEqualTo(PrivacyVisibility.PRIVATE);
		}
	}

	@Test
	void configurableFieldsArePrivateUntilTheCompanyChoosesToShare() {
		for (PrivacyField field : PrivacyField.inCategory(PrivacyCategory.CONFIGURABLE)) {
			assertThat(access.visibilityOf(companyId, field)).isEqualTo(PrivacyVisibility.PRIVATE);
		}

		settings.saveAll(List.of(new CompanyPrivacySetting(companyId, PrivacyField.QUANTITY,
				PrivacyVisibility.SHARED)));

		assertThat(access.visibilityOf(companyId, PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.SHARED);
		assertThat(access.visibilityOf(companyId, PrivacyField.SUPPLIER))
				.isEqualTo(PrivacyVisibility.PRIVATE);
	}

	@Test
	void settingsBelongToTheirCompany() {
		settings.saveAll(List.of(new CompanyPrivacySetting(companyId, PrivacyField.QUANTITY,
				PrivacyVisibility.SHARED)));

		assertThat(access.visibilityOf(UUID.randomUUID(), PrivacyField.QUANTITY))
				.isEqualTo(PrivacyVisibility.PRIVATE);
	}

	@Test
	void sharedFieldsAreTheAlwaysSharedOnesPlusTheConfigurableOnesTheCompanyShares() {
		settings.saveAll(List.of(
				new CompanyPrivacySetting(companyId, PrivacyField.QUANTITY,
						PrivacyVisibility.SHARED),
				new CompanyPrivacySetting(companyId, PrivacyField.SUPPLIER,
						PrivacyVisibility.PRIVATE)));

		assertThat(access.sharedFields(companyId)).containsExactlyInAnyOrder(
				PrivacyField.TRACEABILITY_ID, PrivacyField.RESPONSIBLE_COMPANY,
				PrivacyField.GENEALOGY_RELATIONS, PrivacyField.MATERIAL_TYPE,
				PrivacyField.OPERATIONAL_PHASE, PrivacyField.QUALITY_STATUS,
				PrivacyField.REGISTRATION_DATE, PrivacyField.FINAL_PRODUCT_INDICATOR,
				PrivacyField.QUANTITY);
	}

	@Test
	void sharedFieldsOfACompanyThatNeverConfiguredAnythingAreOnlyTheAlwaysSharedOnes() {
		assertThat(access.sharedFields(companyId))
				.isEqualTo(PrivacyField.inCategory(PrivacyCategory.ALWAYS_SHARED));
	}

	@Test
	void everyFieldIsConsistentBetweenVisibilityOfAndSharedFields() {
		settings.saveAll(List.of(new CompanyPrivacySetting(companyId, PrivacyField.MACHINERY,
				PrivacyVisibility.SHARED)));

		for (PrivacyField field : PrivacyField.values()) {
			assertThat(access.sharedFields(companyId).contains(field))
					.isEqualTo(access.visibilityOf(companyId, field) == PrivacyVisibility.SHARED);
		}
	}

	@Test
	void nullArgumentsAreRejected() {
		assertThatThrownBy(() -> access.visibilityOf(null, PrivacyField.QUANTITY))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> access.visibilityOf(companyId, null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> access.sharedFields(null))
				.isInstanceOf(NullPointerException.class);
	}
}
