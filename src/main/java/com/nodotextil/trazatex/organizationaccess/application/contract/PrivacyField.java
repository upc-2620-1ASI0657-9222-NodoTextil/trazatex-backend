package com.nodotextil.trazatex.organizationaccess.application.contract;

import java.util.EnumSet;
import java.util.Set;

/**
 * The data fields whose visibility between companies is governed by the B2B privacy rules. It is
 * the single source of truth of the three categories: every field declares its own
 * {@link PrivacyCategory}, and a field that does not say otherwise is {@code CONFIGURABLE}
 * (RF-017).
 */
public enum PrivacyField {

	// Always shared (RF-016)
	TRACEABILITY_ID(PrivacyCategory.ALWAYS_SHARED),
	RESPONSIBLE_COMPANY(PrivacyCategory.ALWAYS_SHARED),
	GENEALOGY_RELATIONS(PrivacyCategory.ALWAYS_SHARED),
	MATERIAL_TYPE(PrivacyCategory.ALWAYS_SHARED),
	OPERATIONAL_PHASE(PrivacyCategory.ALWAYS_SHARED),
	QUALITY_STATUS(PrivacyCategory.ALWAYS_SHARED),
	REGISTRATION_DATE(PrivacyCategory.ALWAYS_SHARED),
	FINAL_PRODUCT_INDICATOR(PrivacyCategory.ALWAYS_SHARED),

	// Configurable by each company (RF-017)
	SUPPLIER(PrivacyCategory.CONFIGURABLE),
	GEOGRAPHIC_ORIGIN(PrivacyCategory.CONFIGURABLE),
	QUANTITY(PrivacyCategory.CONFIGURABLE),
	COMPOSITION(PrivacyCategory.CONFIGURABLE),
	RECEPTION_CHARACTERISTICS(PrivacyCategory.CONFIGURABLE),
	MACHINERY(PrivacyCategory.CONFIGURABLE),
	SHRINKAGE(PrivacyCategory.CONFIGURABLE),
	WASTE(PrivacyCategory.CONFIGURABLE),
	WASTE_REASON(PrivacyCategory.CONFIGURABLE),
	FAILURE_CAUSE(PrivacyCategory.CONFIGURABLE),
	QUALITY_TEST_VALUES(PrivacyCategory.CONFIGURABLE),

	// Always private (RF-018)
	OPERATOR_IDENTITY(PrivacyCategory.ALWAYS_PRIVATE),
	INTERNAL_OBSERVATIONS(PrivacyCategory.ALWAYS_PRIVATE),
	FINAL_PRODUCT_COMMERCIAL_DATA(PrivacyCategory.ALWAYS_PRIVATE);

	private final PrivacyCategory category;

	PrivacyField() {
		this(PrivacyCategory.CONFIGURABLE);
	}

	PrivacyField(PrivacyCategory category) {
		this.category = category;
	}

	public PrivacyCategory category() {
		return category;
	}

	public boolean isConfigurable() {
		return category == PrivacyCategory.CONFIGURABLE;
	}

	public static Set<PrivacyField> inCategory(PrivacyCategory category) {
		Set<PrivacyField> fields = EnumSet.noneOf(PrivacyField.class);
		for (PrivacyField field : values()) {
			if (field.category == category) {
				fields.add(field);
			}
		}
		return fields;
	}
}
