package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shows the active company administrator the privacy of every field of their own company, with
 * its category and effective visibility (RF-015).
 */
@Service
public class GetPrivacySettingsUseCase {

	private final OrganizationUserRepository users;
	private final PrivacySettingRepository settings;

	public GetPrivacySettingsUseCase(OrganizationUserRepository users,
			PrivacySettingRepository settings) {
		this.users = users;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public List<PrivacySettingView> execute(UUID adminUserId) {
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		return viewOf(settings.findByCompanyId(admin.companyId()));
	}

	/** Every {@link PrivacyField} with its category and effective visibility. */
	static List<PrivacySettingView> viewOf(List<CompanyPrivacySetting> stored) {
		Map<PrivacyField, PrivacyVisibility> chosen = new EnumMap<>(PrivacyField.class);
		stored.forEach(setting -> chosen.put(setting.field(), setting.visibility()));
		return Arrays.stream(PrivacyField.values())
				.map(field -> new PrivacySettingView(field, field.category(),
						effectiveVisibility(field, chosen)))
				.toList();
	}

	static PrivacyVisibility effectiveVisibility(PrivacyField field,
			Map<PrivacyField, PrivacyVisibility> chosen) {
		if (field.category() == PrivacyCategory.ALWAYS_SHARED) {
			return PrivacyVisibility.SHARED;
		}
		if (field.category() == PrivacyCategory.ALWAYS_PRIVATE) {
			return PrivacyVisibility.PRIVATE;
		}
		return chosen.getOrDefault(field, PrivacyVisibility.PRIVATE);
	}

	public record PrivacySettingView(PrivacyField field, PrivacyCategory category,
			PrivacyVisibility visibility) {
	}
}
