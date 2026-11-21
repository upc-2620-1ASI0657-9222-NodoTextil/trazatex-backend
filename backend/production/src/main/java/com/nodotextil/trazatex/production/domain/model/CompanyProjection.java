package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CompanyProjection {

    private final UUID companyId;
    private String businessName;
    private CompanyStatus status;
    private Instant updatedAt;

    private CompanyProjection(UUID companyId, String businessName, CompanyStatus status, Instant updatedAt) {
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.businessName = businessName;
        this.status = Objects.requireNonNull(status, "status");
        this.updatedAt = updatedAt;
    }

    public static CompanyProjection of(UUID companyId, String businessName, CompanyStatus status, Instant now) {
        return new CompanyProjection(companyId, businessName, status, now);
    }

    public static CompanyProjection restore(UUID companyId, String businessName, CompanyStatus status, Instant updatedAt) {
        return new CompanyProjection(companyId, businessName, status, updatedAt);
    }

    public void changeStatus(CompanyStatus newStatus, Instant now) {
        this.status = Objects.requireNonNull(newStatus, "status");
        this.updatedAt = now;
    }

    public void rename(String newName, Instant now) {
        this.businessName = newName;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == CompanyStatus.ACTIVE;
    }

    public void ensureActive() {
        if (!isActive()) {
            throw new ConflictException(ErrorCodes.COMPANY_INACTIVE,
                    "La empresa está inactiva: no puede iniciar operaciones productivas.");
        }
    }

    public UUID companyId() {
        return companyId;
    }

    public String businessName() {
        return businessName;
    }

    public CompanyStatus status() {
        return status;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
