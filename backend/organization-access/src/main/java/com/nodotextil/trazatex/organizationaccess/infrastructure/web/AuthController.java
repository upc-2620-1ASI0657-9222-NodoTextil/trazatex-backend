package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.LoginCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.LoginResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.AuthenticateUseCase;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.PublicEndpoint;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticateUseCase authentication;

    AuthController(AuthenticateUseCase authentication) {
        this.authentication = authentication;
    }

    record LoginRequest(String email, String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=****]";
        }
    }

    @PostMapping("/login")
    @PublicEndpoint
    LoginResult login(@RequestBody LoginRequest request) {
        return authentication.login(new LoginCommand(request.email(), request.password()));
    }
}
