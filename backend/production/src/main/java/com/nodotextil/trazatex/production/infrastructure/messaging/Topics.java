package com.nodotextil.trazatex.production.infrastructure.messaging;

/** Tópicos que produce production (docs/architecture/communication.md): {@code <servicio>.<agregado>}, clave = aggregateId. */
public final class Topics {

    public static final String BATCH = "production.batch";
    public static final String TRANSFORMATION = "production.transformation";
    public static final String TRANSFER = "production.transfer";

    private Topics() {
    }
}
