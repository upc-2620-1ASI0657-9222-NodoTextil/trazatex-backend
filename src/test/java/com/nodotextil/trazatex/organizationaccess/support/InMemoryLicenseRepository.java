package com.nodotextil.trazatex.organizationaccess.support;

import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import java.util.Optional;

/** In-memory double of the license port for tests. */
public class InMemoryLicenseRepository implements LicenseRepository {

	private License license;

	@Override
	public Optional<License> findFirst() {
		return Optional.ofNullable(license);
	}

	@Override
	public License save(License license) {
		this.license = license;
		return license;
	}
}
