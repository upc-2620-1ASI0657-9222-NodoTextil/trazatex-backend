package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyPrivacySetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "organization_access_privacy_settings", uniqueConstraints = @UniqueConstraint(
		name = "uk_oa_privacy_company_field", columnNames = { "company_id", "field" }))
class PrivacySettingJpaEntity {

	@Id
	UUID id;

	@Column(name = "company_id", nullable = false)
	UUID companyId;

	@Enumerated(EnumType.STRING)
	@Column(name = "field", nullable = false, length = 50)
	PrivacyField field;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	PrivacyVisibility visibility;

	protected PrivacySettingJpaEntity() {
	}

	static PrivacySettingJpaEntity from(CompanyPrivacySetting setting) {
		PrivacySettingJpaEntity entity = new PrivacySettingJpaEntity();
		entity.id = UUID.randomUUID();
		entity.companyId = setting.companyId();
		entity.field = setting.field();
		entity.visibility = setting.visibility();
		return entity;
	}

	CompanyPrivacySetting toDomain() {
		return new CompanyPrivacySetting(companyId, field, visibility);
	}
}
