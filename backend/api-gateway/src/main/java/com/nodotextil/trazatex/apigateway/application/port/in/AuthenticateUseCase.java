package com.nodotextil.trazatex.apigateway.application.port.in;

import com.nodotextil.trazatex.apigateway.domain.model.AuthenticatedUser;
import reactor.core.publisher.Mono;

public interface AuthenticateUseCase {

    Mono<AuthenticatedUser> authenticate(String authorizationHeader);
}
