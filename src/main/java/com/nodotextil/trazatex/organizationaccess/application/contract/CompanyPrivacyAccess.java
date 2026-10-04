package com.nodotextil.trazatex.organizationaccess.application.contract;

import java.util.Set;
import java.util.UUID;


public interface CompanyPrivacyAccess {

	
	PrivacyVisibility visibilityOf(UUID companyId, PrivacyField field);

	
	Set<PrivacyField> sharedFields(UUID companyId);
}
