package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.License;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "organization_access", name = "organization_access_licenses")
class LicenseJpaEntity {

	@Id
	UUID id;

	@Column(nullable = false, unique = true)
	String code;

	@Column(length = 2000)
	String conditions;

	@Column(name = "max_companies", nullable = false)
	int maxCompanies;

	@Column(name = "max_users_per_company", nullable = false)
	int maxUsersPerCompany;

	@Column(name = "created_at", nullable = false)
	LocalDateTime createdAt;

	protected LicenseJpaEntity() {
	}

	static LicenseJpaEntity from(License license) {
		LicenseJpaEntity entity = new LicenseJpaEntity();
		entity.id = license.id();
		entity.code = license.code();
		entity.conditions = license.conditions();
		entity.maxCompanies = license.maxCompanies();
		entity.maxUsersPerCompany = license.maxUsersPerCompany();
		entity.createdAt = license.createdAt();
		return entity;
	}

	License toDomain() {
		return new License(id, code, conditions, maxCompanies, maxUsersPerCompany, createdAt);
	}
}
