package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.CreateAdminRequestUseCase;
import com.nodotextil.trazatex.organizationaccess.application.DecideAdminRequestUseCase;
import com.nodotextil.trazatex.organizationaccess.application.DecideAdminRequestUseCase.Decision;
import com.nodotextil.trazatex.organizationaccess.application.ListAdminRequestsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.ListAdminRequestsUseCase.AdminRequestView;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.AdminRequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin-requests")
public class AdminRequestController {

	private final CreateAdminRequestUseCase createAdminRequest;
	private final ListAdminRequestsUseCase listAdminRequests;
	private final DecideAdminRequestUseCase decideAdminRequest;

	public AdminRequestController(CreateAdminRequestUseCase createAdminRequest,
			ListAdminRequestsUseCase listAdminRequests,
			DecideAdminRequestUseCase decideAdminRequest) {
		this.createAdminRequest = createAdminRequest;
		this.listAdminRequests = listAdminRequests;
		this.decideAdminRequest = decideAdminRequest;
	}

	@PostMapping
	@PreAuthorize("hasRole('COMPANY_ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public AdminRequestResponse create(@Valid @RequestBody CreateAdminRequestRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		AdminRequest created = createAdminRequest.execute(CurrentUser.id(jwt),
				request.firstName(), request.lastName(), request.email(), request.jobTitle());
		return AdminRequestResponse.from(created, null, null);
	}

	@GetMapping
	@PreAuthorize("hasRole('LICENSE_OWNER')")
	public List<AdminRequestResponse> list(
			@RequestParam(required = false) AdminRequestStatus status) {
		return listAdminRequests.execute(status).stream()
				.map(view -> AdminRequestResponse.from(view.request(), view.companyLegalName(),
						null))
				.toList();
	}

	@PatchMapping("/{id}/decision")
	@PreAuthorize("hasRole('LICENSE_OWNER')")
	public AdminRequestResponse decide(@PathVariable UUID id,
			@Valid @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
		Decision decision = decideAdminRequest.execute(id,
				request.decision() == DecisionRequest.Decision.APPROVED, CurrentUser.id(jwt));
		return AdminRequestResponse.from(decision.request(), null,
				decision.invitation().map(invitation -> invitation.id()).orElse(null));
	}

	public record CreateAdminRequestRequest(@NotBlank String firstName, @NotBlank String lastName,
			@NotBlank @Email String email, @NotBlank String jobTitle) {
	}

	public record DecisionRequest(@NotNull Decision decision) {

		public enum Decision {
			APPROVED, REJECTED
		}
	}

	public record AdminRequestResponse(UUID id, UUID companyId, String companyLegalName,
			String email, String firstName, String lastName, String jobTitle,
			AdminRequestStatus status, LocalDateTime createdAt, LocalDateTime decidedAt,
			UUID invitationId) {

		static AdminRequestResponse from(AdminRequest request, String companyLegalName,
				UUID invitationId) {
			return new AdminRequestResponse(request.id(), request.companyId(), companyLegalName,
					request.email(), request.firstName(), request.lastName(), request.jobTitle(),
					request.status(), request.createdAt(), request.decidedAt(), invitationId);
		}
	}
}
