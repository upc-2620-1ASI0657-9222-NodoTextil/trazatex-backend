package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.ChangeUserStatusUseCase;
import com.nodotextil.trazatex.organizationaccess.application.ListCompanyUsersUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ListCompanyUsersUseCase listCompanyUsers;

	@MockitoBean
	private ChangeUserStatusUseCase changeUserStatus;

	private final UUID adminId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();
	private final OrganizationUser operator = OrganizationUser.create("op@example.com", "Op",
			"Erator", "Dyer", "secret-hash", Role.OPERATOR, companyId, NOW);

	@Test
	void anAdministratorListsTheUsersOfTheirCompany() throws Exception {
		when(listCompanyUsers.execute(adminId)).thenReturn(List.of(operator));

		mockMvc.perform(get("/api/users").with(TestJwt.companyAdmin(adminId, companyId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(operator.id().toString()))
				.andExpect(jsonPath("$[0].email").value("op@example.com"))
				.andExpect(jsonPath("$[0].jobTitle").value("Dyer"))
				.andExpect(jsonPath("$[0].role").value("OPERATOR"))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void anAdministratorChangesAUserStatus() throws Exception {
		when(changeUserStatus.execute(adminId, operator.id(), UserStatus.INACTIVE))
				.thenReturn(operator.withStatus(UserStatus.INACTIVE));

		mockMvc.perform(patch("/api/users/{id}/status", operator.id())
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void reactivatingWithoutASeatAnswers409() throws Exception {
		when(changeUserStatus.execute(any(), any(), any()))
				.thenThrow(new OrganizationConflictException("The company has reached its user limit"));

		mockMvc.perform(patch("/api/users/{id}/status", operator.id())
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void usersOfAnotherCompanyAnswer403() throws Exception {
		when(changeUserStatus.execute(any(), any(), any())).thenThrow(
				new OrganizationAccessDeniedException("You can only manage your own company"));

		mockMvc.perform(patch("/api/users/{id}/status", UUID.randomUUID())
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void theStatusIsRequired() throws Exception {
		mockMvc.perform(patch("/api/users/{id}/status", operator.id())
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(changeUserStatus);
	}

	@Test
	void onlyCompanyAdministratorsManageUsers() throws Exception {
		mockMvc.perform(get("/api/users").with(TestJwt.operator(adminId, companyId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/users").with(TestJwt.licenseOwner(adminId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/users/{id}/status", operator.id())
				.with(TestJwt.operator(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
		verifyNoInteractions(listCompanyUsers, changeUserStatus);
	}
}
