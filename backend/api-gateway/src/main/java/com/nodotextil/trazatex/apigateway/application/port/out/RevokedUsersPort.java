package com.nodotextil.trazatex.apigateway.application.port.out;

import java.util.UUID;

/** Usuarios inactivados (RF-012), alimentado por {@code UserAccountUpdated}. En memoria: se reconstruye releyendo el tópico. */
public interface RevokedUsersPort {

    /**
     * @return {@code false} mientras no se haya terminado de leer el tópico al arrancar: hasta entonces no se sabe quién está
     *         inactivo y no se debe dejar pasar a nadie
     */
    boolean isLoaded();

    boolean isRevoked(UUID userId);
}
