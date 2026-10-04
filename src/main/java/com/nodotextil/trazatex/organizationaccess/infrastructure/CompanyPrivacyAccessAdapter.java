package com.nodotextil.trazatex.organizationaccess.infrastructure;

import com.nodotextil.trazatex.organizationaccess.application.contract.CompanyPrivacyAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implements the {@link CompanyPrivacyAccess} contract on top of the stored settings. */
@Component
@Transactional(readOnly = true)
class CompanyPrivacyAccessAdapter implements CompanyPrivacyAccess {

	private final PrivacySettingRepository settings;

	CompanyPrivacyAccessAdapter(PrivacySettingRepository settings) {
		this.settings = settings;
	}

	@Override
	public PrivacyVisibility visibilityOf(UUID companyId, PrivacyField field) {
		Objects.requireNonNull(companyId, "companyId");
		Objects.requireNonNull(field, "field");
		return switch (field.category()) {
			case ALWAYS_SHARED -> PrivacyVisibility.SHARED;
			case ALWAYS_PRIVATE -> PrivacyVisibility.PRIVATE;
			case CONFIGURABLE -> settings.findByCompanyId(companyId).stream()
					.filter(setting -> setting.field() == field)
					.map(CompanyPrivacySetting::visibility).findFirst()
					.orElse(PrivacyVisibility.PRIVATE);
		};
	}

	@Override
	public Set<PrivacyField> sharedFields(UUID companyId) {
		Objects.requireNonNull(companyId, "companyId");
		Set<PrivacyField> shared = EnumSet.copyOf(
				PrivacyField.inCategory(PrivacyCategory.ALWAYS_SHARED));
		List<CompanyPrivacySetting> stored = settings.findByCompanyId(companyId);
		stored.stream().filter(setting -> setting.visibility() == PrivacyVisibility.SHARED)
				.forEach(setting -> shared.add(setting.field()));
		return shared;
	}
}
