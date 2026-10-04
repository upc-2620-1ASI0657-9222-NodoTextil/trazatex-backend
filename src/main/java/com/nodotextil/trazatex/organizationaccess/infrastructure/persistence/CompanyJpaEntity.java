package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "organization_access_companies")
class CompanyJpaEntity {

	@Id
	UUID id;

	@Column(name = "license_id", nullable = false)
	UUID licenseId;

	@Column(nullable = false, unique = true, length = 11)
	String ruc;

	@Column(name = "legal_name", nullable = false)
	String legalName;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "organization_access_company_activities",
			joinColumns = @JoinColumn(name = "company_id"))
	@Column(name = "activity", nullable = false)
	Set<String> activities = new HashSet<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	CompanyStatus status;

	@Column(name = "created_at", nullable = false)
	LocalDateTime createdAt;

	protected CompanyJpaEntity() {
	}

	static CompanyJpaEntity from(Company company) {
		CompanyJpaEntity entity = new CompanyJpaEntity();
		entity.id = company.id();
		entity.licenseId = company.licenseId();
		entity.ruc = company.ruc();
		entity.legalName = company.legalName();
		entity.activities = new HashSet<>(company.activities());
		entity.status = company.status();
		entity.createdAt = company.createdAt();
		return entity;
	}

	Company toDomain() {
		return new Company(id, licenseId, ruc, legalName, activities, status, createdAt);
	}
}
