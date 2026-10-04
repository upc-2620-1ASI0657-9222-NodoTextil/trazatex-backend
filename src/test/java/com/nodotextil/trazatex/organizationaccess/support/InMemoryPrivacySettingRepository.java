package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.PrivacySettingRepository;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory double of the privacy-setting port for tests; counts the writes it receives. */
public class InMemoryPrivacySettingRepository implements PrivacySettingRepository {

	private final Map<String, CompanyPrivacySetting> store = new LinkedHashMap<>();
	public int saveAllCalls = 0;

	@Override
	public List<CompanyPrivacySetting> findByCompanyId(UUID companyId) {
		return store.values().stream().filter(setting -> setting.companyId().equals(companyId))
				.toList();
	}

	@Override
	public void saveAll(Collection<CompanyPrivacySetting> settings) {
		saveAllCalls++;
		settings.forEach(setting -> store.put(setting.companyId() + "/" + setting.field(),
				setting));
	}

	public int size() {
		return store.size();
	}
}
