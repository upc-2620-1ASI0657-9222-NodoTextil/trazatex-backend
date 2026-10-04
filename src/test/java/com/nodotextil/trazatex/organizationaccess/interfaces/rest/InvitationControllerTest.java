package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static com.nodotextil.trazatex.organizationaccess.support.TestData.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.AcceptInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CancelInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetInvitationUseCase.InvitationView;
import com.nodotextil.trazatex.organizationaccess.application.InviteOperatorUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvitationController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class InvitationControllerTest {

	private static final String INVITE_BODY = "{\"firstName\":\"Luis\",\"lastName\":\"Perez\","
			+ "\"email\":\"luis@example.com\",\"jobTitle\":\"Dyer\"}";
	private static final String ACCEPT_BODY = "{\"password\":\"a-long-secure-password\","
			+ "\"passwordConfirmation\":\"a-long-secure-password\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InviteOperatorUseCase inviteOperator;

	@MockitoBean
	private CancelInvitationUseCase cancelInvitation;

	@MockitoBean
	private GetInvitationUseCase getInvitation;

	@MockitoBean
	private AcceptInvitationUseCase acceptInvitation;

	private final UUID adminId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();
	private final Invitation invitation = Invitation.issue("luis@example.com", companyId,
			Role.OPERATOR, "secret-token", "Luis", "Perez", "Dyer", adminId, NOW);

	@Test
	void anAdministratorInvitesAnOperator() throws Exception {
		when(inviteOperator.execute(adminId, "Luis", "Perez", "luis@example.com", "Dyer"))
				.thenReturn(invitation);

		mockMvc.perform(post("/api/invitations/operators")
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(invitation.id().toString()))
				.andExpect(jsonPath("$.role").value("OPERATOR"))
				.andExpect(jsonPath("$.firstName").value("Luis"))
				.andExpect(jsonPath("$.jobTitle").value("Dyer"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void missingNamesAndJobTitleAreRejected() throws Exception {
		for (String body : new String[] {
				"{\"lastName\":\"Perez\",\"email\":\"a@example.com\",\"jobTitle\":\"Dyer\"}",
				"{\"firstName\":\"Luis\",\"email\":\"a@example.com\",\"jobTitle\":\"Dyer\"}",
				"{\"firstName\":\"Luis\",\"lastName\":\"Perez\",\"email\":\"a@example.com\"}",
				"{\"firstName\":\"Luis\",\"lastName\":\"Perez\",\"jobTitle\":\"Dyer\"}" }) {
			mockMvc.perform(post("/api/invitations/operators")
					.with(TestJwt.companyAdmin(adminId, companyId))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(inviteOperator);
	}

	@Test
	void noSeatLeftAnswers409() throws Exception {
		when(inviteOperator.execute(any(), any(), any(), any(), any()))
				.thenThrow(new OrganizationConflictException("The company has reached its user limit"));

		mockMvc.perform(post("/api/invitations/operators")
				.with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void onlyCompanyAdministratorsInviteOperators() throws Exception {
		mockMvc.perform(post("/api/invitations/operators")
				.with(TestJwt.operator(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/invitations/operators").with(TestJwt.licenseOwner(adminId))
				.contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/invitations/operators")
				.contentType(MediaType.APPLICATION_JSON).content(INVITE_BODY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(inviteOperator);
	}

	@Test
	void theIssuerCancelsAnInvitation() throws Exception {
		when(cancelInvitation.execute(invitation.id(), adminId)).thenReturn(invitation.cancel());

		mockMvc.perform(post("/api/invitations/{id}/cancel", invitation.id())
				.with(TestJwt.companyAdmin(adminId, companyId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		mockMvc.perform(post("/api/invitations/{id}/cancel", invitation.id())
				.with(TestJwt.licenseOwner(adminId))).andExpect(status().isOk());
		verify(cancelInvitation, org.mockito.Mockito.times(2)).execute(invitation.id(), adminId);
	}

	@Test
	void cancellingSomeoneElsesOrANonPendingInvitationIsRejected() throws Exception {
		when(cancelInvitation.execute(any(), any()))
				.thenThrow(new OrganizationAccessDeniedException("Only the issuer can cancel"))
				.thenThrow(new OrganizationConflictException("Only a pending invitation"));

		mockMvc.perform(post("/api/invitations/{id}/cancel", invitation.id())
				.with(TestJwt.companyAdmin(adminId, companyId)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/invitations/{id}/cancel", invitation.id())
				.with(TestJwt.companyAdmin(adminId, companyId))).andExpect(status().isConflict());
	}

	@Test
	void operatorsCannotCancel() throws Exception {
		mockMvc.perform(post("/api/invitations/{id}/cancel", invitation.id())
				.with(TestJwt.operator(adminId, companyId))).andExpect(status().isForbidden());
		verifyNoInteractions(cancelInvitation);
	}

	@Test
	void theInvitationIsPublicAndPrefillsTheForm() throws Exception {
		when(getInvitation.execute("secret-token")).thenReturn(new InvitationView(invitation,
				"Hilandería Andina SAC", InvitationStatus.PENDING));

		mockMvc.perform(get("/api/invitations/secret-token")).andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("luis@example.com"))
				.andExpect(jsonPath("$.role").value("OPERATOR"))
				.andExpect(jsonPath("$.firstName").value("Luis"))
				.andExpect(jsonPath("$.lastName").value("Perez"))
				.andExpect(jsonPath("$.jobTitle").value("Dyer"))
				.andExpect(jsonPath("$.companyLegalName").value("Hilandería Andina SAC"))
				.andExpect(jsonPath("$.expiresAt").exists())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void anUnknownTokenAnswers404() throws Exception {
		when(getInvitation.execute("unknown"))
				.thenThrow(new OrganizationNotFoundException("Invitation not found"));

		mockMvc.perform(get("/api/invitations/unknown")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void acceptingIsPublicAndCreatesTheUser() throws Exception {
		OrganizationUser user = OrganizationUser.create("luis@example.com", "Luis", "Perez",
				"Dyer", "hash", Role.OPERATOR, companyId, NOW);
		when(acceptInvitation.execute("secret-token", "a-long-secure-password",
				"a-long-secure-password", null, null)).thenReturn(user);

		mockMvc.perform(post("/api/invitations/secret-token/accept")
				.contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(user.id().toString()))
				.andExpect(jsonPath("$.email").value("luis@example.com"))
				.andExpect(jsonPath("$.role").value("OPERATOR"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void theConfirmationIsRequiredToAccept() throws Exception {
		mockMvc.perform(post("/api/invitations/secret-token/accept")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"a-long-secure-password\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(acceptInvitation);
	}

	@Test
	void useCaseRejectionsMapToTheirStatus() throws Exception {
		when(acceptInvitation.execute(any(), any(), any(), any(), any()))
				.thenThrow(new OrganizationValidationException("The password and its confirmation do not match",
						Map.of("passwordConfirmation", "Does not match the password")))
				.thenThrow(new OrganizationConflictException("The invitation is EXPIRED and cannot be used"));

		mockMvc.perform(post("/api/invitations/secret-token/accept")
				.contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.passwordConfirmation").exists());
		mockMvc.perform(post("/api/invitations/secret-token/accept")
				.contentType(MediaType.APPLICATION_JSON).content(ACCEPT_BODY))
				.andExpect(status().isConflict());
	}
}
