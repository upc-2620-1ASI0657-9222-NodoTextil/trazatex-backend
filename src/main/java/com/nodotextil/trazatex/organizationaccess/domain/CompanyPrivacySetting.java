package com.nodotextil.trazatex.organizationaccess.domain;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import java.util.Map;
import java.util.UUID;

/**
 * What a company chose for one {@code CONFIGURABLE} field. Only configurable fields are stored:
 * the others have a fixed visibility, and a configurable field without a setting is private.
 */
public record CompanyPrivacySetting(UUID companyId, PrivacyField field,
		PrivacyVisibility visibility) {

	public CompanyPrivacySetting {
		if (companyId == null || field == null || visibility == null) {
			throw new OrganizationValidationException(
					"A privacy setting needs a company, a field and a visibility");
		}
		if (!field.isConfigurable()) {
			throw new OrganizationValidationException(
					field + " is " + field.category() + " and cannot be configured",
					Map.of(field.name(), "Is " + field.category() + " and cannot be configured"));
		}
	}
}
