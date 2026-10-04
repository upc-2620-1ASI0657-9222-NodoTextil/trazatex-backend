package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.ChangeUserStatusUseCase;
import com.nodotextil.trazatex.organizationaccess.application.ListCompanyUsersUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class UserController {

	private final ListCompanyUsersUseCase listCompanyUsers;
	private final ChangeUserStatusUseCase changeUserStatus;

	public UserController(ListCompanyUsersUseCase listCompanyUsers,
			ChangeUserStatusUseCase changeUserStatus) {
		this.listCompanyUsers = listCompanyUsers;
		this.changeUserStatus = changeUserStatus;
	}

	@GetMapping
	public List<UserResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return listCompanyUsers.execute(CurrentUser.id(jwt)).stream().map(UserResponse::from)
				.toList();
	}

	@PatchMapping("/{id}/status")
	public UserResponse changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeUserStatusRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(changeUserStatus.execute(CurrentUser.id(jwt), id,
				request.status()));
	}

	public record ChangeUserStatusRequest(@NotNull UserStatus status) {
	}

	public record UserResponse(UUID id, String email, String firstName, String lastName,
			String jobTitle, Role role, UserStatus status) {

		static UserResponse from(OrganizationUser user) {
			return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName(),
					user.jobTitle(), user.role(), user.status());
		}
	}
}
