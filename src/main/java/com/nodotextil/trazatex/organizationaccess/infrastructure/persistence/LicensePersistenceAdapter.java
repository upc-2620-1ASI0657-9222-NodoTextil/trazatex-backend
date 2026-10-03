package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LicensePersistenceAdapter implements LicenseRepository {

	private final SpringDataLicenseRepository repository;

	LicensePersistenceAdapter(SpringDataLicenseRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<License> findFirst() {
		return repository.findFirstByOrderByCreatedAtAsc().map(LicenseJpaEntity::toDomain);
	}

	@Override
	public License save(License license) {
		return repository.save(LicenseJpaEntity.from(license)).toDomain();
	}
}
