package com.nodotextil.trazatex.organizationaccess.domain.event;

import java.util.UUID;

/** Agregado en V2. Tópico {@code orgaccess.invitation}, clave requestId. Lo consume notification, que avisa al dueño de la licencia. */
public record AdminRequestSubmitted(UUID requestId, UUID companyId) implements OrganizationEvent {
    @Override public String type() { return "AdminRequestSubmitted"; }
    @Override public String aggregateId() { return requestId.toString(); }
}

