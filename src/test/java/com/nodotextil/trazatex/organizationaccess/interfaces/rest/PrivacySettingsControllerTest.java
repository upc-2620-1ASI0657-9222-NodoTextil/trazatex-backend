package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase.PrivacySettingView;
import com.nodotextil.trazatex.organizationaccess.application.UpdatePrivacySettingsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.UpdatePrivacySettingsUseCase.Requested;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import com.nodotextil.trazatex.organizationaccess.support.TestJwt;
import com.nodotextil.trazatex.shared.security.JwtConfiguration;
import com.nodotextil.trazatex.shared.security.SecurityConfig;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrivacySettingsController.class)
@Import({ SecurityConfig.class, JwtConfiguration.class })
class PrivacySettingsControllerTest {

	private static final String BODY = "{\"settings\":[{\"field\":\"QUANTITY\","
			+ "\"visibility\":\"SHARED\"}]}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetPrivacySettingsUseCase getPrivacySettings;

	@MockitoBean
	private UpdatePrivacySettingsUseCase updatePrivacySettings;

	private final UUID adminId = UUID.randomUUID();
	private final UUID companyId = UUID.randomUUID();

	private List<PrivacySettingView> fullView(PrivacyField shared) {
		return Arrays.stream(PrivacyField.values()).map(field -> new PrivacySettingView(field,
				field.category(), switch (field.category()) {
					case ALWAYS_SHARED -> PrivacyVisibility.SHARED;
					case ALWAYS_PRIVATE -> PrivacyVisibility.PRIVATE;
					case CONFIGURABLE -> field == shared ? PrivacyVisibility.SHARED
							: PrivacyVisibility.PRIVATE;
				})).toList();
	}

	@Test
	void getReturnsTheCompleteConfiguration() throws Exception {
		when(getPrivacySettings.execute(adminId)).thenReturn(fullView(null));

		mockMvc.perform(get("/api/privacy-settings").with(TestJwt.companyAdmin(adminId, companyId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.settings.length()").value(PrivacyField.values().length))
				.andExpect(jsonPath("$.settings[?(@.field=='TRACEABILITY_ID')].category")
						.value("ALWAYS_SHARED"))
				.andExpect(jsonPath("$.settings[?(@.field=='TRACEABILITY_ID')].visibility")
						.value("SHARED"))
				.andExpect(jsonPath("$.settings[?(@.field=='QUANTITY')].category")
						.value("CONFIGURABLE"))
				.andExpect(jsonPath("$.settings[?(@.field=='QUANTITY')].visibility")
						.value("PRIVATE"))
				.andExpect(jsonPath("$.settings[?(@.field=='OPERATOR_IDENTITY')].visibility")
						.value("PRIVATE"));
	}

	@Test
	void putChangesTheFieldsAndReturnsTheCompleteConfiguration() throws Exception {
		when(updatePrivacySettings.execute(adminId, List.of(new Requested(PrivacyField.QUANTITY,
				PrivacyVisibility.SHARED)))).thenReturn(fullView(PrivacyField.QUANTITY));

		mockMvc.perform(put("/api/privacy-settings").with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.settings.length()").value(PrivacyField.values().length))
				.andExpect(jsonPath("$.settings[?(@.field=='QUANTITY')].visibility")
						.value("SHARED"));
	}

	@Test
	void anInvalidFieldAnswers400WithTheFieldErrors() throws Exception {
		when(updatePrivacySettings.execute(any(), any())).thenThrow(
				new OrganizationValidationException("The privacy settings are not valid",
						Map.of("QUALITY_STATUS", "Is ALWAYS_SHARED and cannot be configured")));

		mockMvc.perform(put("/api/privacy-settings").with(TestJwt.companyAdmin(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"settings\":[{\"field\":\"QUALITY_STATUS\",\"visibility\":\"PRIVATE\"}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.QUALITY_STATUS").exists());
	}

	@Test
	void malformedBodiesAreRejectedBeforeTheUseCase() throws Exception {
		for (String body : new String[] { "{}", "{\"settings\":[]}",
				"{\"settings\":[{\"visibility\":\"SHARED\"}]}",
				"{\"settings\":[{\"field\":\"QUANTITY\"}]}",
				"{\"settings\":[{\"field\":\"NOT_A_FIELD\",\"visibility\":\"SHARED\"}]}",
				"{\"settings\":[{\"field\":\"QUANTITY\",\"visibility\":\"MAYBE\"}]}" }) {
			mockMvc.perform(put("/api/privacy-settings")
					.with(TestJwt.companyAdmin(adminId, companyId))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(updatePrivacySettings);
	}

	@Test
	void anInactiveAdministratorIsDeniedByTheRule() throws Exception {
		when(getPrivacySettings.execute(any()))
				.thenThrow(new OrganizationAccessDeniedException("Only an active administrator"));

		mockMvc.perform(get("/api/privacy-settings").with(TestJwt.companyAdmin(adminId, companyId)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void onlyCompanyAdministratorsCanReadOrChange() throws Exception {
		mockMvc.perform(get("/api/privacy-settings").with(TestJwt.operator(adminId, companyId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/privacy-settings").with(TestJwt.operator(adminId, companyId))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/privacy-settings").with(TestJwt.licenseOwner(adminId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/privacy-settings").with(TestJwt.licenseOwner(adminId))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/privacy-settings")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/privacy-settings").contentType(MediaType.APPLICATION_JSON)
				.content(BODY)).andExpect(status().isUnauthorized());
		verifyNoInteractions(getPrivacySettings, updatePrivacySettings);
	}
}
