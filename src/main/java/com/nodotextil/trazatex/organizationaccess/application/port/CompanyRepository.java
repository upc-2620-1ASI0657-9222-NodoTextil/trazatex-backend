package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

	Company save(Company company);

	Optional<Company> findById(UUID id);

	List<Company> findAll();

	long countByLicenseId(UUID licenseId);

	boolean existsByRuc(String ruc);
}
