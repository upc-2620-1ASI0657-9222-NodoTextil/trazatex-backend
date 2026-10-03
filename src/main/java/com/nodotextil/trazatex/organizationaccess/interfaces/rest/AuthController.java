package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.application.AuthenticateUserUseCase;
import com.nodotextil.trazatex.organizationaccess.application.AuthenticateUserUseCase.AuthenticationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticateUserUseCase authenticateUser;

	public AuthController(AuthenticateUserUseCase authenticateUser) {
		this.authenticateUser = authenticateUser;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		AuthenticationResult result = authenticateUser.execute(request.email(),
				request.password());
		return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(),
				result.userId(), result.companyId(), result.role());
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record LoginResponse(String accessToken, String tokenType, long expiresIn,
			UUID userId, UUID companyId, String role) {
	}
}
