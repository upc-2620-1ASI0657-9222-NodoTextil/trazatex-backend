package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetPrivacySettingsUseCase.PrivacySettingView;
import com.nodotextil.trazatex.organizationaccess.application.UpdatePrivacySettingsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.UpdatePrivacySettingsUseCase.Requested;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyCategory;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/privacy-settings")
public class PrivacySettingsController {

	private final GetPrivacySettingsUseCase getPrivacySettings;
	private final UpdatePrivacySettingsUseCase updatePrivacySettings;

	public PrivacySettingsController(GetPrivacySettingsUseCase getPrivacySettings,
			UpdatePrivacySettingsUseCase updatePrivacySettings) {
		this.getPrivacySettings = getPrivacySettings;
		this.updatePrivacySettings = updatePrivacySettings;
	}

	@GetMapping
	@PreAuthorize("hasRole('COMPANY_ADMIN')")
	public PrivacySettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
		return PrivacySettingsResponse.from(getPrivacySettings.execute(CurrentUser.id(jwt)));
	}

	@PutMapping
	@PreAuthorize("hasRole('COMPANY_ADMIN')")
	public PrivacySettingsResponse update(@Valid @RequestBody UpdatePrivacySettingsRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		List<Requested> requested = request.settings().stream()
				.map(setting -> new Requested(setting.field(), setting.visibility())).toList();
		return PrivacySettingsResponse
				.from(updatePrivacySettings.execute(CurrentUser.id(jwt), requested));
	}

	public record UpdatePrivacySettingsRequest(@NotEmpty List<@Valid @NotNull SettingRequest> settings) {
	}

	public record SettingRequest(@NotNull PrivacyField field, @NotNull PrivacyVisibility visibility) {
	}

	public record PrivacySettingsResponse(List<SettingResponse> settings) {

		static PrivacySettingsResponse from(List<PrivacySettingView> views) {
			return new PrivacySettingsResponse(views.stream().map(view -> new SettingResponse(
					view.field(), view.category(), view.visibility())).toList());
		}
	}

	public record SettingResponse(PrivacyField field, PrivacyCategory category,
			PrivacyVisibility visibility) {
	}
}
