package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase.LicenseOverview;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/license")
@PreAuthorize("hasRole('LICENSE_OWNER')")
public class LicenseController {

	private final GetLicenseOverviewUseCase getLicenseOverview;

	public LicenseController(GetLicenseOverviewUseCase getLicenseOverview) {
		this.getLicenseOverview = getLicenseOverview;
	}

	@GetMapping
	public LicenseResponse get() {
		LicenseOverview overview = getLicenseOverview.execute();
		return new LicenseResponse(overview.license().code(), overview.license().conditions(),
				overview.license().maxCompanies(), overview.license().maxUsersPerCompany(),
				overview.usedCompanies(),
				overview.companies().stream()
						.map(usage -> new CompanyUsageResponse(usage.company().id(),
								usage.company().legalName(), usage.company().ruc(),
								usage.company().status(), usage.activeUsers()))
						.toList());
	}

	public record LicenseResponse(String code, String conditions, int maxCompanies,
			int maxUsersPerCompany, long usedCompanies, List<CompanyUsageResponse> companies) {
	}

	public record CompanyUsageResponse(UUID companyId, String legalName, String ruc,
			CompanyStatus status, long activeUsers) {
	}
}
