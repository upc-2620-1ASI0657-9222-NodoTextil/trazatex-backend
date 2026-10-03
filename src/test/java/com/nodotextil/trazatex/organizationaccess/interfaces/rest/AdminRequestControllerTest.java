package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.CreateAdminRequestUseCase;
import com.nodotextil.trazatex.organizationaccess.application.DecideAdminRequestUseCase;
import com.nodotextil.trazatex.organizationaccess.application.DecideAdminRequestUseCase.Decision;
import com.nodotextil.trazatex.organizationaccess.application.ListAdminRequestsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.ListAdminRequestsUseCase.AdminRequestView;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminRequestController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class AdminRequestControllerTest {

	private static final String CREATE_BODY = "{\"firstName\":\"Rosa\",\"lastName\":\"Diaz\","
			+ "\"email\":\"rosa@example.com\",\"jobTitle\":\"Manager\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CreateAdminRequestUseCase createAdminRequest;

	@MockitoBean
	private ListAdminRequestsUseCase listAdminRequests;

	@MockitoBean
	private DecideAdminRequestUseCase decideAdminRequest;

	private final UUID adminId = UUID.randomUUID();
	private final UUID ownerId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();
	private final AdminRequest request = AdminRequest.create(companyId, "rosa@example.com",
			"Rosa", "Diaz", "Manager", adminId, NOW);

	@Test
	void anAdministratorCreatesARequest() throws Exception {
		when(createAdminRequest.execute(adminId, "Rosa", "Diaz", "rosa@example.com", "Manager"))
				.thenReturn(request);

		mockMvc.perform(post("/api/admin-requests").with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.firstName").value("Rosa"))
				.andExpect(jsonPath("$.lastName").value("Diaz"))
				.andExpect(jsonPath("$.jobTitle").value("Manager"));
	}

	@Test
	void namesAndJobTitleAreMandatory() throws Exception {
		for (String body : new String[] {
				"{\"lastName\":\"Diaz\",\"email\":\"r@example.com\",\"jobTitle\":\"M\"}",
				"{\"firstName\":\"Rosa\",\"email\":\"r@example.com\",\"jobTitle\":\"M\"}",
				"{\"firstName\":\"Rosa\",\"lastName\":\"Diaz\",\"email\":\"r@example.com\"}",
				"{\"firstName\":\"Rosa\",\"lastName\":\"Diaz\",\"jobTitle\":\"M\"}" }) {
			mockMvc.perform(post("/api/admin-requests")
					.with(TestJwt.companyAdmin(adminId, companyId))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(createAdminRequest);
	}

	@Test
	void aDuplicatedPendingRequestAnswers409() throws Exception {
		when(createAdminRequest.execute(any(), any(), any(), any(), any()))
				.thenThrow(new OrganizationConflictException("There is already a pending request"));

		mockMvc.perform(post("/api/admin-requests").with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isConflict());
	}

	@Test
	void onlyCompanyAdministratorsCanCreateRequests() throws Exception {
		mockMvc.perform(post("/api/admin-requests").with(TestJwt.licenseOwner(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin-requests").with(TestJwt.operator(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isForbidden());
		verifyNoInteractions(createAdminRequest);
	}

	@Test
	void theOwnerListsRequestsWithAnOptionalStatusFilter() throws Exception {
		when(listAdminRequests.execute(AdminRequestStatus.PENDING))
				.thenReturn(List.of(new AdminRequestView(request, "Textiles SAC")));

		mockMvc.perform(get("/api/admin-requests").param("status", "PENDING")
				.with(TestJwt.licenseOwner(ownerId))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].companyLegalName").value("Textiles SAC"))
				.andExpect(jsonPath("$[0].email").value("rosa@example.com"));
	}

	@Test
	void administratorsCannotListRequests() throws Exception {
		mockMvc.perform(get("/api/admin-requests").with(TestJwt.companyAdmin(adminId, companyId)))
				.andExpect(status().isForbidden());
		verifyNoInteractions(listAdminRequests);
	}

	@Test
	void theOwnerApprovesARequestAndGetsTheInvitationId() throws Exception {
		Invitation invitation = Invitation.issue("rosa@example.com", companyId,
				Role.COMPANY_ADMIN, "token", "Rosa", "Diaz", "Manager", ownerId, NOW);
		when(decideAdminRequest.execute(request.id(), true, ownerId)).thenReturn(
				new Decision(request.approve(ownerId, NOW), Optional.of(invitation)));

		mockMvc.perform(patch("/api/admin-requests/{id}/decision", request.id())
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVED\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.invitationId").value(invitation.id().toString()));
	}

	@Test
	void theOwnerRejectsARequest() throws Exception {
		when(decideAdminRequest.execute(request.id(), false, ownerId)).thenReturn(
				new Decision(request.reject(ownerId, NOW), Optional.empty()));

		mockMvc.perform(patch("/api/admin-requests/{id}/decision", request.id())
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"REJECTED\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.invitationId").doesNotExist());
	}

	@Test
	void approvingWithoutASeatAnswers409() throws Exception {
		when(decideAdminRequest.execute(any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
				.thenThrow(new OrganizationConflictException("The company has reached its user limit"));

		mockMvc.perform(patch("/api/admin-requests/{id}/decision", request.id())
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVED\"}")).andExpect(status().isConflict());
	}

	@Test
	void theDecisionIsRequiredAndOnlyTheOwnerDecides() throws Exception {
		mockMvc.perform(patch("/api/admin-requests/{id}/decision", request.id())
				.with(TestJwt.licenseOwner(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"PENDING\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/admin-requests/{id}/decision", request.id())
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(decideAdminRequest);
	}
}
