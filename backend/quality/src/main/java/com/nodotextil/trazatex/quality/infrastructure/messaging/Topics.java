package com.nodotextil.trazatex.quality.infrastructure.messaging;

/** Tópicos que produce quality (docs/architecture/communication.md): {@code <servicio>.<agregado>}, clave = aggregateId. */
public final class Topics {

    public static final String CONTROL = "quality.control";
    public static final String FAILURE = "quality.failure";

    private Topics() {
    }
}
