package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.application.port.PrivacyAuditPort.PrivacyChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "organization_access", name = "organization_access_privacy_audit",
		indexes = @Index(name = "idx_oa_privacy_audit_company", columnList = "company_id"))
class PrivacyAuditJpaEntity {

	@Id
	UUID id;

	@Column(name = "company_id", nullable = false)
	UUID companyId;

	@Column(name = "changed_by_user_id", nullable = false)
	UUID changedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "field", nullable = false, length = 50)
	PrivacyField field;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_visibility", nullable = false, length = 20)
	PrivacyVisibility previousVisibility;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_visibility", nullable = false, length = 20)
	PrivacyVisibility newVisibility;

	@Column(name = "occurred_at", nullable = false)
	LocalDateTime occurredAt;

	protected PrivacyAuditJpaEntity() {
	}

	static PrivacyAuditJpaEntity from(PrivacyChange change) {
		PrivacyAuditJpaEntity entity = new PrivacyAuditJpaEntity();
		entity.id = UUID.randomUUID();
		entity.companyId = change.companyId();
		entity.changedByUserId = change.changedByUserId();
		entity.field = change.field();
		entity.previousVisibility = change.previous();
		entity.newVisibility = change.current();
		entity.occurredAt = change.occurredAt();
		return entity;
	}
}
