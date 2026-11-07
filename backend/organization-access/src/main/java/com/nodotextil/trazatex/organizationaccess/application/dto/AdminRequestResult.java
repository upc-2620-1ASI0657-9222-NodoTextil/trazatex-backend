package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code invitationId} solo viene al aprobar. */
public record AdminRequestResult(
        UUID id,
        UUID companyId,
        UUID requestedBy,
        String fullName,
        String email,
        String position,
        AdminRequestStatus status,
        Instant createdAt,
        Instant resolvedAt,
        UUID resolvedBy,
        UUID invitationId) {

    public static AdminRequestResult from(AdminRequest request) {
        return from(request, null);
    }

    public static AdminRequestResult from(AdminRequest request, UUID invitationId) {
        return new AdminRequestResult(request.id(), request.companyId(), request.requestedBy(), request.fullName(),
                request.email().value(), request.position(), request.status(), request.createdAt(),
                request.resolvedAt(), request.resolvedBy(), invitationId);
    }
}
