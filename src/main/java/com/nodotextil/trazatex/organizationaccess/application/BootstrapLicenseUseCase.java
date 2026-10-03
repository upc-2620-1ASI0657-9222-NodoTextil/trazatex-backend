package com.nodotextil.trazatex.organizationaccess.application;

import com.nodotextil.trazatex.organizationaccess.application.port.LicenseRepository;
import com.nodotextil.trazatex.organizationaccess.application.port.OrganizationUserRepository;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development bootstrap: creates the license and its {@code LICENSE_OWNER} when they do not
 * exist yet. Safe to run on every start.
 */
@Service
public class BootstrapLicenseUseCase {

	static final int MIN_PASSWORD_LENGTH = 12;

	private final LicenseRepository licenses;
	private final OrganizationUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	@Autowired
	public BootstrapLicenseUseCase(LicenseRepository licenses, OrganizationUserRepository users,
			PasswordEncoder passwordEncoder) {
		this(licenses, users, passwordEncoder, Clock.systemUTC());
	}

	BootstrapLicenseUseCase(LicenseRepository licenses, OrganizationUserRepository users,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.licenses = licenses;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	public void execute(String licenseCode, int maxCompanies, int maxUsersPerCompany,
			String ownerEmail, String ownerPassword) {
		if (ownerEmail == null || ownerEmail.isBlank() || ownerPassword == null
				|| ownerPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new OrganizationValidationException(
					"The bootstrap needs an owner email and a password of at least "
							+ MIN_PASSWORD_LENGTH + " characters");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (licenses.findFirst().isEmpty()) {
			licenses.save(License.create(licenseCode, null, maxCompanies, maxUsersPerCompany,
					now));
		}
		if (users.findByEmail(ownerEmail).isEmpty()) {
			users.save(OrganizationUser.create(ownerEmail, "License", "Owner", null,
					passwordEncoder.encode(ownerPassword), Role.LICENSE_OWNER, null, now));
		}
	}
}
