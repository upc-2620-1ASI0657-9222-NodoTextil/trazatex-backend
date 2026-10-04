package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase.PrivacySettingView;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort.PrivacyChange;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UpdatePrivacySettingsUseCase {

	private final OrganizationUserRepository users;
	private final PrivacySettingRepository settings;
	private final PrivacyAuditPort audit;
	private final Clock clock;

	@Autowired
	public UpdatePrivacySettingsUseCase(OrganizationUserRepository users,
			PrivacySettingRepository settings, PrivacyAuditPort audit) {
		this(users, settings, audit, Clock.systemUTC());
	}

	UpdatePrivacySettingsUseCase(OrganizationUserRepository users,
			PrivacySettingRepository settings, PrivacyAuditPort audit, Clock clock) {
		this.users = users;
		this.settings = settings;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public List<PrivacySettingView> execute(UUID adminUserId, List<Requested> requested) {
		OrganizationUser admin = ActingUsers.activeCompanyAdmin(users, adminUserId);
		UUID companyId = admin.companyId();
		List<CompanyPrivacySetting> toApply = validate(companyId, requested);

		Map<PrivacyField, PrivacyVisibility> current = new EnumMap<>(PrivacyField.class);
		settings.findByCompanyId(companyId)
				.forEach(setting -> current.put(setting.field(), setting.visibility()));
		LocalDateTime now = LocalDateTime.now(clock);
		List<CompanyPrivacySetting> changed = new ArrayList<>();
		List<PrivacyChange> changes = new ArrayList<>();
		for (CompanyPrivacySetting setting : toApply) {
			PrivacyVisibility previous = GetPrivacySettingsUseCase
					.effectiveVisibility(setting.field(), current);
			if (previous != setting.visibility()) {
				changed.add(setting);
				changes.add(new PrivacyChange(companyId, admin.id(), setting.field(), previous,
						setting.visibility(), now));
			}
		}
		if (!changed.isEmpty()) {
			settings.saveAll(changed);
			changes.forEach(audit::record);
		}
		return GetPrivacySettingsUseCase.viewOf(settings.findByCompanyId(companyId));
	}

	
	private static List<CompanyPrivacySetting> validate(UUID companyId,
			List<Requested> requested) {
		if (requested == null || requested.isEmpty()) {
			throw new OrganizationValidationException("At least one setting is required",
					Map.of("settings", "At least one setting is required"));
		}
		Map<String, String> errors = new LinkedHashMap<>();
		Set<PrivacyField> seen = EnumSet.noneOf(PrivacyField.class);
		List<CompanyPrivacySetting> valid = new ArrayList<>();
		for (Requested item : requested) {
			if (item == null || item.field() == null || item.visibility() == null) {
				errors.put("settings", "Every setting needs a field and a visibility");
			}
			else if (!item.field().isConfigurable()) {
				errors.put(item.field().name(),
						"Is " + item.field().category() + " and cannot be configured");
			}
			else if (!seen.add(item.field())) {
				errors.put(item.field().name(), "Is repeated in the request");
			}
			else {
				valid.add(new CompanyPrivacySetting(companyId, item.field(), item.visibility()));
			}
		}
		if (!errors.isEmpty()) {
			throw new OrganizationValidationException(
					"The privacy settings are not valid; nothing was changed", errors);
		}
		return valid;
	}

	public record Requested(PrivacyField field, PrivacyVisibility visibility) {
	}
}
