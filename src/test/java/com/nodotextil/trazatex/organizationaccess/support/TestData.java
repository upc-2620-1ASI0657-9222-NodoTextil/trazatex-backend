package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** Builders for the domain objects tests need. */
public final class TestData {

	public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private TestData() {
	}

	public static License license(int maxCompanies, int maxUsersPerCompany) {
		return License.create("LIC-1", "Perpetual license", maxCompanies, maxUsersPerCompany,
				NOW);
	}

	public static Company company(License license, String ruc) {
		return Company.create(license.id(), ruc, "Textiles " + ruc + " SAC", Set.of("WEAVING"),
				NOW);
	}

	public static OrganizationUser user(String email, Role role, UUID companyId) {
		return OrganizationUser.create(email, "First", "Last", "Job", "hash", role, companyId,
				NOW);
	}
}
