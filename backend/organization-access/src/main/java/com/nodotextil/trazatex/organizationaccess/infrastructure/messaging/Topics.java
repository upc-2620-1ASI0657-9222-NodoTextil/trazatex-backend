package com.nodotextil.trazatex.organizationaccess.infrastructure.messaging;

/** Tópicos que produce organization-access (docs/architecture/communication.md): {@code <servicio>.<agregado>}, clave = aggregateId. */
public final class Topics {

    public static final String COMPANY = "orgaccess.company";
    public static final String INVITATION = "orgaccess.invitation";
    public static final String USER = "orgaccess.user";

    private Topics() {
    }
}
