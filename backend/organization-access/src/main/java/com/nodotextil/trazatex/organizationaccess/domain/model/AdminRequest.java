package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitud de un {@code COMPANY_ADMIN} para incorporar otro administrador (US07), que el {@code LICENSE_OWNER} aprueba o
 * rechaza (US08). La capacidad de usuarios se valida antes de aprobar ({@link License#ensureUserCapacity}); si falta, la
 * solicitud sigue {@code PENDING}.
 */
public final class AdminRequest {

    private final UUID id;
    private final UUID companyId;
    private final UUID requestedBy;
    private final String fullName;
    private final Email email;
    private final String position;
    private AdminRequestStatus status;
    private final Instant createdAt;
    private Instant resolvedAt;
    private UUID resolvedBy;

    private AdminRequest(UUID id, UUID companyId, UUID requestedBy, String fullName, Email email, String position,
                         AdminRequestStatus status, Instant createdAt, Instant resolvedAt, UUID resolvedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.companyId = companyId;
        this.requestedBy = requestedBy;
        this.fullName = fullName == null ? null : fullName.trim();
        this.email = email;
        this.position = position == null ? null : position.trim();
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }

    /** Nombre, correo y cargo son obligatorios; si falta alguno el error 400 los lista. */
    public static AdminRequest create(UUID id, UUID companyId, UUID requestedBy, String fullName, String email,
                                      String position, Instant now) {
        new RequiredFields()
                .text("fullName", fullName)
                .text("email", email)
                .text("position", position)
                .verify();
        return new AdminRequest(id, companyId, requestedBy, fullName, new Email(email), position,
                AdminRequestStatus.PENDING, now, null, null);
    }

    public static AdminRequest restore(UUID id, UUID companyId, UUID requestedBy, String fullName, Email email,
                                       String position, AdminRequestStatus status, Instant createdAt,
                                       Instant resolvedAt, UUID resolvedBy) {
        return new AdminRequest(id, companyId, requestedBy, fullName, email, position, status, createdAt, resolvedAt,
                resolvedBy);
    }

    public void approve(UUID resolver, Instant now) {
        resolve(AdminRequestStatus.APPROVED, resolver, now);
    }

    public void reject(UUID resolver, Instant now) {
        resolve(AdminRequestStatus.REJECTED, resolver, now);
    }

    private void resolve(AdminRequestStatus decision, UUID resolver, Instant now) {
        if (status != AdminRequestStatus.PENDING) {
            throw new ConflictException(ErrorCodes.ADMIN_REQUEST_NOT_PENDING, "La solicitud ya fue resuelta.");
        }
        status = decision;
        resolvedBy = Objects.requireNonNull(resolver, "resolver");
        resolvedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public String fullName() {
        return fullName;
    }

    public Email email() {
        return email;
    }

    public String position() {
        return position;
    }

    public AdminRequestStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public UUID resolvedBy() {
        return resolvedBy;
    }
}
