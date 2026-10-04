package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PrivacySettingRepository {

	List<CompanyPrivacySetting> findByCompanyId(UUID companyId);

	
	void saveAll(Collection<CompanyPrivacySetting> settings);
}
