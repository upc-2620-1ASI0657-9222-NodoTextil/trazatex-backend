package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.CompanyRepository;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CompanyPersistenceAdapter implements CompanyRepository {

	private final SpringDataCompanyRepository repository;

	CompanyPersistenceAdapter(SpringDataCompanyRepository repository) {
		this.repository = repository;
	}

	@Override
	public Company save(Company company) {
		return repository.save(CompanyJpaEntity.from(company)).toDomain();
	}

	@Override
	public Optional<Company> findById(UUID id) {
		return repository.findById(id).map(CompanyJpaEntity::toDomain);
	}

	@Override
	public List<Company> findAll() {
		return repository.findAllByOrderByCreatedAtAsc().stream()
				.map(CompanyJpaEntity::toDomain).toList();
	}

	@Override
	public long countByLicenseId(UUID licenseId) {
		return repository.countByLicenseId(licenseId);
	}

	@Override
	public boolean existsByRuc(String ruc) {
		return repository.existsByRuc(ruc);
	}
}
