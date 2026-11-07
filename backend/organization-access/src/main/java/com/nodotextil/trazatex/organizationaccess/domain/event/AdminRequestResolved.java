package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;

import java.util.UUID;

/** Tópico {@code orgaccess.invitation}, clave requestId. Lo consume notification. {@code decision} es APPROVED o REJECTED. */
public record AdminRequestResolved(
        UUID requestId,
        UUID companyId,
        UUID requesterUserId,
        AdminRequestStatus decision) implements OrganizationEvent {

    public static final String TYPE = "AdminRequestResolved";

    public AdminRequestResolved {
        if (decision == AdminRequestStatus.PENDING) {
            throw new IllegalArgumentException("Una solicitud pendiente no está resuelta");
        }
    }

    public static AdminRequestResolved of(AdminRequest request) {
        return new AdminRequestResolved(request.id(), request.companyId(), request.requestedBy(), request.status());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return requestId.toString();
    }
}
