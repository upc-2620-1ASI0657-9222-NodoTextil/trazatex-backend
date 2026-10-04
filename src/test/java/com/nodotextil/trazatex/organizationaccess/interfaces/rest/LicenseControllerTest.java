package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase.CompanyUsage;
import com.nodotextil.trazatex.organizationaccess.application.GetLicenseOverviewUseCase.LicenseOverview;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.License;
import com.nodotextil.trazatex.organizationaccess.support.TestData;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LicenseController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class LicenseControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetLicenseOverviewUseCase getLicenseOverview;

	@Test
	void licenseOwnerSeesConditionsLimitsAndUsage() throws Exception {
		License license = TestData.license(3, 10);
		Company company = TestData.company(license, "20123456789");
		when(getLicenseOverview.execute()).thenReturn(new LicenseOverview(license, 1,
				List.of(new CompanyUsage(company, 4))));

		mockMvc.perform(get("/api/license").with(TestJwt.licenseOwner(UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("LIC-1"))
				.andExpect(jsonPath("$.conditions").value("Perpetual license"))
				.andExpect(jsonPath("$.maxCompanies").value(3))
				.andExpect(jsonPath("$.maxUsersPerCompany").value(10))
				.andExpect(jsonPath("$.usedCompanies").value(1))
				.andExpect(jsonPath("$.companies[0].companyId").value(company.id().toString()))
				.andExpect(jsonPath("$.companies[0].activeUsers").value(4));
	}

	@Test
	void otherRolesAreForbidden() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();

		mockMvc.perform(get("/api/license").with(TestJwt.companyAdmin(userId, companyId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/license").with(TestJwt.operator(userId, companyId)))
				.andExpect(status().isForbidden());
		verifyNoInteractions(getLicenseOverview);
	}

	@Test
	void anonymousCallersAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/license")).andExpect(status().isUnauthorized());
	}
}
