package com.nodotextil.trazatex.apigateway.application.port.out;

import com.nodotextil.trazatex.apigateway.domain.model.AuthenticatedUser;
import reactor.core.publisher.Mono;

public interface TokenVerifierPort {

    Mono<AuthenticatedUser> verify(String token);
}
