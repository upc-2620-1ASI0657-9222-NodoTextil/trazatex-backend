package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.BootstrapLicenseUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
class DevelopmentBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevelopmentBootstrap.class);

	private final BootstrapLicenseUseCase bootstrap;
	private final String ownerEmail;
	private final String ownerPassword;
	private final String licenseCode;
	private final int maxCompanies;
	private final int maxUsersPerCompany;

	DevelopmentBootstrap(BootstrapLicenseUseCase bootstrap,
			@Value("${BOOTSTRAP_OWNER_EMAIL:}") String ownerEmail,
			@Value("${BOOTSTRAP_OWNER_PASSWORD:}") String ownerPassword,
			@Value("${BOOTSTRAP_LICENSE_CODE:LOCAL-DEV}") String licenseCode,
			@Value("${BOOTSTRAP_MAX_COMPANIES:5}") int maxCompanies,
			@Value("${BOOTSTRAP_MAX_USERS_PER_COMPANY:20}") int maxUsersPerCompany) {
		this.bootstrap = bootstrap;
		this.ownerEmail = ownerEmail;
		this.ownerPassword = ownerPassword;
		this.licenseCode = licenseCode;
		this.maxCompanies = maxCompanies;
		this.maxUsersPerCompany = maxUsersPerCompany;
	}

	@Override
	public void run(ApplicationArguments args) {
		bootstrap.execute(licenseCode, maxCompanies, maxUsersPerCompany, ownerEmail,
				ownerPassword);
		log.info("Development bootstrap done: license '{}' and owner '{}'", licenseCode,
				ownerEmail);
	}
}
