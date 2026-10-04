package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.AuthenticateUserUseCase;
import com.nodotextil.trazatex.organizationaccess.application.AuthenticateUserUseCase.AuthenticationResult;
import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.organizationaccess.domain.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.infrastructure.security.ActiveMembershipSecurityConfiguration;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class, ActiveMembershipSecurityConfiguration.class })
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthenticateUserUseCase authenticateUser;

	@MockitoBean
	private OrganizationAccess organizationAccess;

	@Test
	void loginIsPublicAndReturnsTheToken() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		when(authenticateUser.execute("ana@example.com", "secret")).thenReturn(
				new AuthenticationResult("jwt-value", 3600, userId, companyId, "COMPANY_ADMIN"));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@example.com\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("jwt-value"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.userId").value(userId.toString()))
				.andExpect(jsonPath("$.companyId").value(companyId.toString()))
				.andExpect(jsonPath("$.role").value("COMPANY_ADMIN"));
	}

	@Test
	void invalidCredentialsAnswer401WithTheModuleErrorBody() throws Exception {
		when(authenticateUser.execute("ana@example.com", "wrong"))
				.thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@example.com\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("Invalid credentials"))
				.andExpect(jsonPath("$.fieldErrors").isMap());
	}

	@Test
	void blankFieldsAreRejected() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest());
	}
}
