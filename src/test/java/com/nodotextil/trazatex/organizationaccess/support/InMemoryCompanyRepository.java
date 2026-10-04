package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory double of the company port for tests. */
public class InMemoryCompanyRepository implements CompanyRepository {

	private final Map<UUID, Company> store = new LinkedHashMap<>();

	@Override
	public Company save(Company company) {
		store.put(company.id(), company);
		return company;
	}

	@Override
	public Optional<Company> findById(UUID id) {
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public List<Company> findAll() {
		return List.copyOf(store.values());
	}

	@Override
	public long countByLicenseId(UUID licenseId) {
		return store.values().stream().filter(company -> company.licenseId().equals(licenseId))
				.count();
	}

	@Override
	public boolean existsByRuc(String ruc) {
		return store.values().stream().anyMatch(company -> company.ruc().equals(ruc));
	}
}
