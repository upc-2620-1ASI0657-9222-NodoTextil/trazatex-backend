package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import java.time.LocalDateTime;
import java.util.UUID;


public interface PrivacyAuditPort {

	void record(PrivacyChange change);

	
	record PrivacyChange(UUID companyId, UUID changedByUserId, PrivacyField field,
			PrivacyVisibility previous, PrivacyVisibility current, LocalDateTime occurredAt) {
	}
}
