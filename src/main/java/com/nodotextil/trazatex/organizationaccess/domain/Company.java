package com.nodotextil.trazatex.organizationaccess.domain;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** A company of the textile chain that belongs to a license. */
public final class Company {

	private static final Pattern RUC = Pattern.compile("\\d{11}");

	private final UUID id;
	private final UUID licenseId;
	private final String ruc;
	private final String legalName;
	private final Set<String> activities;
	private final CompanyStatus status;
	private final LocalDateTime createdAt;

	public Company(UUID id, UUID licenseId, String ruc, String legalName, Set<String> activities,
			CompanyStatus status, LocalDateTime createdAt) {
		this.id = DomainText.notNull(id, "id");
		this.licenseId = DomainText.notNull(licenseId, "licenseId");
		this.ruc = DomainText.required(ruc, "ruc");
		if (!RUC.matcher(this.ruc).matches()) {
			throw new OrganizationValidationException("ruc must have 11 digits");
		}
		this.legalName = DomainText.required(legalName, "legalName");
		this.activities = Set.copyOf(DomainText.notNull(activities, "activities"));
		this.status = DomainText.notNull(status, "status");
		this.createdAt = DomainText.notNull(createdAt, "createdAt");
	}

	public static Company create(UUID licenseId, String ruc, String legalName,
			Set<String> activities, LocalDateTime now) {
		return new Company(UUID.randomUUID(), licenseId, ruc, legalName, activities,
				CompanyStatus.ACTIVE, now);
	}

	public boolean isActive() {
		return status == CompanyStatus.ACTIVE;
	}

	public UUID id() {
		return id;
	}

	public UUID licenseId() {
		return licenseId;
	}

	public String ruc() {
		return ruc;
	}

	public String legalName() {
		return legalName;
	}

	public Set<String> activities() {
		return activities;
	}

	public CompanyStatus status() {
		return status;
	}

	public LocalDateTime createdAt() {
		return createdAt;
	}
}
