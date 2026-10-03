package com.nodotextil.trazatex.organizationaccess.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** The perpetual license that bounds the chain: how many companies and users per company. */
public final class License {

	private final UUID id;
	private final String code;
	private final String conditions;
	private final int maxCompanies;
	private final int maxUsersPerCompany;
	private final LocalDateTime createdAt;

	public License(UUID id, String code, String conditions, int maxCompanies,
			int maxUsersPerCompany, LocalDateTime createdAt) {
		if (maxCompanies < 1 || maxUsersPerCompany < 1) {
			throw new OrganizationValidationException("License limits must be at least 1");
		}
		this.id = DomainText.notNull(id, "id");
		this.code = DomainText.required(code, "code");
		this.conditions = DomainText.optional(conditions);
		this.maxCompanies = maxCompanies;
		this.maxUsersPerCompany = maxUsersPerCompany;
		this.createdAt = DomainText.notNull(createdAt, "createdAt");
	}

	public static License create(String code, String conditions, int maxCompanies,
			int maxUsersPerCompany, LocalDateTime now) {
		return new License(UUID.randomUUID(), code, conditions, maxCompanies,
				maxUsersPerCompany, now);
	}

	public boolean allowsAnotherCompany(long currentCompanies) {
		return currentCompanies < maxCompanies;
	}

	public boolean allowsAnotherUser(long currentUsers) {
		return currentUsers < maxUsersPerCompany;
	}

	public UUID id() {
		return id;
	}

	public String code() {
		return code;
	}

	public String conditions() {
		return conditions;
	}

	public int maxCompanies() {
		return maxCompanies;
	}

	public int maxUsersPerCompany() {
		return maxUsersPerCompany;
	}

	public LocalDateTime createdAt() {
		return createdAt;
	}
}
