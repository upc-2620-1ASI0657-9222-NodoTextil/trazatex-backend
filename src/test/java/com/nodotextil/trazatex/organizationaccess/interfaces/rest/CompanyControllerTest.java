package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.ChangeCompanyStatusUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CreateCompanyUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CreateCompanyUseCase.CreatedCompany;
import com.nodotextil.trazatex.organizationaccess.application.ListCompaniesUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.Company;
import com.nodotextil.trazatex.organizationaccess.domain.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompanyController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class CompanyControllerTest {

	private static final String CREATE_BODY = "{\"legalName\":\"Textiles SAC\","
			+ "\"ruc\":\"20123456789\",\"activities\":[\"WEAVING\"],"
			+ "\"adminEmail\":\"admin@example.com\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CreateCompanyUseCase createCompany;

	@MockitoBean
	private ListCompaniesUseCase listCompanies;

	@MockitoBean
	private ChangeCompanyStatusUseCase changeCompanyStatus;

	private final UUID ownerId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();
	private final Company company = new Company(companyId, UUID.randomUUID(), "20123456789",
			"Textiles SAC", Set.of("WEAVING"), CompanyStatus.ACTIVE, NOW);

	@Test
	void licenseOwnerCreatesACompany() throws Exception {
		Invitation invitation = Invitation.issue("admin@example.com", companyId,
				Role.COMPANY_ADMIN, "token", null, null, null, ownerId, NOW);
		when(createCompany.execute("Textiles SAC", "20123456789", Set.of("WEAVING"),
				"admin@example.com", ownerId)).thenReturn(new CreatedCompany(company, invitation));

		mockMvc.perform(post("/api/companies").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.company.id").value(companyId.toString()))
				.andExpect(jsonPath("$.company.ruc").value("20123456789"))
				.andExpect(jsonPath("$.company.status").value("ACTIVE"))
				.andExpect(jsonPath("$.adminInvitationId").value(invitation.id().toString()))
				.andExpect(jsonPath("$.token").doesNotExist())
				.andExpect(jsonPath("$.adminInvitationToken").doesNotExist());
	}

	@Test
	void limitReachedAnswers409() throws Exception {
		when(createCompany.execute(any(), any(), any(), any(), any()))
				.thenThrow(new OrganizationConflictException("The license has reached its limit"));

		mockMvc.perform(post("/api/companies").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void invalidRucAnswers400WithFieldErrors() throws Exception {
		when(createCompany.execute(any(), any(), any(), any(), any()))
				.thenThrow(new OrganizationValidationException("The RUC is not valid in SUNAT",
						Map.of("ruc", "Must belong to an active and habido taxpayer")));

		mockMvc.perform(post("/api/companies").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.ruc").exists());
	}

	@Test
	void sunatOutageAnswers503() throws Exception {
		when(createCompany.execute(any(), any(), any(), any(), any()))
				.thenThrow(new ExternalServiceUnavailableException("SUNAT validation is unavailable"));

		mockMvc.perform(post("/api/companies").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
	}

	@Test
	void malformedInputIsRejectedBeforeTheUseCase() throws Exception {
		mockMvc.perform(post("/api/companies").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"legalName\":\"X\",\"ruc\":\"123\",\"activities\":[],"
						+ "\"adminEmail\":\"nope\"}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(createCompany);
	}

	@Test
	void listsCompanies() throws Exception {
		when(listCompanies.execute()).thenReturn(java.util.List.of(company));

		mockMvc.perform(get("/api/companies").with(TestJwt.licenseOwner(ownerId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].legalName").value("Textiles SAC"))
				.andExpect(jsonPath("$[0].activities[0]").value("WEAVING"));
	}

	@Test
	void changesTheStatusOfACompany() throws Exception {
		when(changeCompanyStatus.execute(companyId, CompanyStatus.INACTIVE))
				.thenReturn(company.withStatus(CompanyStatus.INACTIVE));

		mockMvc.perform(patch("/api/companies/{id}/status", companyId)
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));
		verify(changeCompanyStatus).execute(eq(companyId), eq(CompanyStatus.INACTIVE));
	}

	@Test
	void unknownCompanyAnswers404() throws Exception {
		when(changeCompanyStatus.execute(any(), any()))
				.thenThrow(new OrganizationNotFoundException("Company not found"));

		mockMvc.perform(patch("/api/companies/{id}/status", UUID.randomUUID())
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void onlyTheLicenseOwnerCanManageCompanies() throws Exception {
		UUID userId = UUID.randomUUID();
		mockMvc.perform(post("/api/companies").with(TestJwt.companyAdmin(userId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/companies").with(TestJwt.operator(userId, companyId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/companies/{id}/status", companyId)
				.with(TestJwt.companyAdmin(userId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(createCompany, listCompanies, changeCompanyStatus);
	}

	@Test
	void anonymousCallersAreUnauthorized() throws Exception {
		mockMvc.perform(get("/api/companies")).andExpect(status().isUnauthorized());
	}
}
