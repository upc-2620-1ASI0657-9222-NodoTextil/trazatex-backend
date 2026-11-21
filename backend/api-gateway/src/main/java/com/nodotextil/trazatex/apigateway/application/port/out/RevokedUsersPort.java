package com.nodotextil.trazatex.apigateway.application.port.out;

import java.util.UUID;

public interface RevokedUsersPort {

    boolean isLoaded();

    boolean isRevoked(UUID userId);
}
