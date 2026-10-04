package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.AcceptInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.CancelInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetInvitationUseCase;
import com.nodotextil.trazatex.organizationaccess.application.GetInvitationUseCase.InvitationView;
import com.nodotextil.trazatex.organizationaccess.application.InviteOperatorUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitations. {@code GET /{token}} and {@code POST /{token}/accept} are public (see
 * {@code SecurityConfig}); the rest declare their roles with {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

	private final InviteOperatorUseCase inviteOperator;
	private final CancelInvitationUseCase cancelInvitation;
	private final GetInvitationUseCase getInvitation;
	private final AcceptInvitationUseCase acceptInvitation;

	public InvitationController(InviteOperatorUseCase inviteOperator,
			CancelInvitationUseCase cancelInvitation, GetInvitationUseCase getInvitation,
			AcceptInvitationUseCase acceptInvitation) {
		this.inviteOperator = inviteOperator;
		this.cancelInvitation = cancelInvitation;
		this.getInvitation = getInvitation;
		this.acceptInvitation = acceptInvitation;
	}

	@PostMapping("/operators")
	@PreAuthorize("hasRole('COMPANY_ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationResponse inviteOperator(@Valid @RequestBody InviteOperatorRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		Invitation invitation = inviteOperator.execute(CurrentUser.id(jwt), request.firstName(),
				request.lastName(), request.email(), request.jobTitle());
		return InvitationResponse.from(invitation);
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasAnyRole('LICENSE_OWNER', 'COMPANY_ADMIN')")
	public InvitationResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		return InvitationResponse.from(cancelInvitation.execute(id, CurrentUser.id(jwt)));
	}

	@GetMapping("/{token}")
	public InvitationDetailResponse get(@PathVariable String token) {
		InvitationView view = getInvitation.execute(token);
		Invitation invitation = view.invitation();
		return new InvitationDetailResponse(invitation.email(), invitation.role(),
				invitation.firstName(), invitation.lastName(), invitation.jobTitle(),
				view.companyLegalName(), invitation.expiresAt(), view.status());
	}

	@PostMapping("/{token}/accept")
	@ResponseStatus(HttpStatus.CREATED)
	public AcceptedUserResponse accept(@PathVariable String token,
			@Valid @RequestBody AcceptInvitationRequest request) {
		OrganizationUser user = acceptInvitation.execute(token, request.password(),
				request.passwordConfirmation(), request.firstName(), request.lastName());
		return new AcceptedUserResponse(user.id(), user.email(), user.role(), user.companyId());
	}

	public record InviteOperatorRequest(@NotBlank String firstName, @NotBlank String lastName,
			@NotBlank @Email String email, @NotBlank String jobTitle) {
	}

	public record AcceptInvitationRequest(@NotBlank String password,
			@NotBlank String passwordConfirmation, String firstName, String lastName) {
	}

	/** The token is never returned: it only travels in the invitation email. */
	public record InvitationResponse(UUID id, String email, Role role, String firstName,
			String lastName, String jobTitle, InvitationStatus status, LocalDateTime expiresAt) {

		static InvitationResponse from(Invitation invitation) {
			return new InvitationResponse(invitation.id(), invitation.email(), invitation.role(),
					invitation.firstName(), invitation.lastName(), invitation.jobTitle(),
					invitation.status(), invitation.expiresAt());
		}
	}

	public record InvitationDetailResponse(String email, Role role, String firstName,
			String lastName, String jobTitle, String companyLegalName, LocalDateTime expiresAt,
			InvitationStatus status) {
	}

	public record AcceptedUserResponse(UUID userId, String email, Role role, UUID companyId) {
	}
}
