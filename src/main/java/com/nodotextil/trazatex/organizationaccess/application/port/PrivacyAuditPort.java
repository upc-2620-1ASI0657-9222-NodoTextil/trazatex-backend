package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import java.time.LocalDateTime;
import java.util.UUID;

/** The module's own audit trail for privacy changes, until a shared audit exists. */
public interface PrivacyAuditPort {

	void record(PrivacyChange change);

	/** One field whose effective visibility changed, and who changed it. */
	record PrivacyChange(UUID companyId, UUID changedByUserId, PrivacyField field,
			PrivacyVisibility previous, PrivacyVisibility current, LocalDateTime occurredAt) {
	}
}
