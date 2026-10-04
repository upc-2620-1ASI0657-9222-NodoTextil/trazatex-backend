package com.nodotextil.trazatex.organizationaccess.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PrivacyFieldTest {

	@Test
	void everyFieldHasACategory() {
		assertThat(PrivacyField.values()).allSatisfy(field -> assertThat(field.category())
				.isNotNull());
	}

	@Test
	void alwaysSharedFieldsMatchRf016() {
		assertThat(PrivacyField.inCategory(PrivacyCategory.ALWAYS_SHARED)).containsExactlyInAnyOrder(
				PrivacyField.TRACEABILITY_ID, PrivacyField.RESPONSIBLE_COMPANY,
				PrivacyField.GENEALOGY_RELATIONS, PrivacyField.MATERIAL_TYPE,
				PrivacyField.OPERATIONAL_PHASE, PrivacyField.QUALITY_STATUS,
				PrivacyField.REGISTRATION_DATE, PrivacyField.FINAL_PRODUCT_INDICATOR);
	}

	@Test
	void configurableFieldsMatchRf017() {
		assertThat(PrivacyField.inCategory(PrivacyCategory.CONFIGURABLE)).containsExactlyInAnyOrder(
				PrivacyField.SUPPLIER, PrivacyField.GEOGRAPHIC_ORIGIN, PrivacyField.QUANTITY,
				PrivacyField.COMPOSITION, PrivacyField.RECEPTION_CHARACTERISTICS,
				PrivacyField.MACHINERY, PrivacyField.SHRINKAGE, PrivacyField.WASTE,
				PrivacyField.WASTE_REASON, PrivacyField.FAILURE_CAUSE,
				PrivacyField.QUALITY_TEST_VALUES);
	}

	@Test
	void alwaysPrivateFieldsMatchRf018() {
		assertThat(PrivacyField.inCategory(PrivacyCategory.ALWAYS_PRIVATE))
				.containsExactlyInAnyOrder(PrivacyField.OPERATOR_IDENTITY,
						PrivacyField.INTERNAL_OBSERVATIONS,
						PrivacyField.FINAL_PRODUCT_COMMERCIAL_DATA);
	}

	@Test
	void theThreeCategoriesPartitionEveryField() {
		var all = EnumSet.noneOf(PrivacyField.class);
		for (PrivacyCategory category : PrivacyCategory.values()) {
			var fields = PrivacyField.inCategory(category);
			assertThat(all).doesNotContainAnyElementsOf(fields);
			all.addAll(fields);
		}
		assertThat(all).containsExactlyInAnyOrder(PrivacyField.values());
		assertThat(Arrays.stream(PrivacyField.values()).filter(PrivacyField::isConfigurable))
				.hasSize(11);
	}

	@Test
	void onlyConfigurableFieldsCanHaveAStoredSetting() {
		UUID companyId = UUID.randomUUID();

		assertThat(new CompanyPrivacySetting(companyId, PrivacyField.QUANTITY,
				PrivacyVisibility.SHARED).visibility()).isEqualTo(PrivacyVisibility.SHARED);
		for (PrivacyField field : PrivacyField.values()) {
			if (!field.isConfigurable()) {
				assertThatThrownBy(() -> new CompanyPrivacySetting(companyId, field,
						PrivacyVisibility.SHARED))
						.isInstanceOf(OrganizationValidationException.class);
			}
		}
	}
}
