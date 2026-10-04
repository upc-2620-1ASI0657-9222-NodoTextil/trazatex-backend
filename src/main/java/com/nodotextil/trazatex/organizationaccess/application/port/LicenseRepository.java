package com.nodotextil.trazatex.organizationaccess.application.port;

import com.nodotextil.trazatex.organizationaccess.domain.License;
import java.util.Optional;

public interface LicenseRepository {

	
	Optional<License> findFirst();

	License save(License license);
}
