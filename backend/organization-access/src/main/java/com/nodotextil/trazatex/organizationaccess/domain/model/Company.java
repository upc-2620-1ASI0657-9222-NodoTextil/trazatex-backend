package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Empresa textil de la cadena (US02, US03). Nace {@code ACTIVE}. Inactivarla conserva todo su historial; los efectos
 * sobre las operaciones productivas los aplica Production con su proyección. La validez del RUC ante Chequea.pe
 * (ACTIVO y HABIDO) y la unicidad del RUC se comprueban antes, en la capa de aplicación.
 */
public final class Company {

    private final UUID id;
    private final Ruc ruc;
    private final String businessName;
    private final Set<TextileActivity> activities;
    private CompanyStatus status;
    private final Instant createdAt;

    private Company(UUID id, Ruc ruc, String businessName, Collection<TextileActivity> activities,
                    CompanyStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ruc = ruc;
        this.businessName = businessName == null ? null : businessName.trim();
        this.activities = activities == null || activities.isEmpty()
                ? EnumSet.noneOf(TextileActivity.class)
                : EnumSet.copyOf(activities);
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Company register(UUID id, Ruc ruc, String businessName, Collection<TextileActivity> activities, Instant now) {
        Company company = new Company(id, ruc, businessName, activities, CompanyStatus.ACTIVE, now);
        new RequiredFields()
                .value("ruc", ruc)
                .text("businessName", businessName)
                .notEmpty("activities", activities)
                .verify();
        return company;
    }

    public static Company restore(UUID id, Ruc ruc, String businessName, Collection<TextileActivity> activities,
                                  CompanyStatus status, Instant createdAt) {
        return new Company(id, ruc, businessName, activities, status, createdAt);
    }

    public void deactivate() {
        changeStatus(CompanyStatus.ACTIVE, CompanyStatus.INACTIVE, "inactiva");
    }

    public void activate() {
        changeStatus(CompanyStatus.INACTIVE, CompanyStatus.ACTIVE, "activa");
    }

    private void changeStatus(CompanyStatus expected, CompanyStatus target, String targetName) {
        if (status != expected) {
            throw new ConflictException(ErrorCodes.INVALID_STATUS_TRANSITION, "La empresa ya está " + targetName + ".");
        }
        status = target;
    }

    public boolean isActive() {
        return status == CompanyStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public Ruc ruc() {
        return ruc;
    }

    public String businessName() {
        return businessName;
    }

    public Set<TextileActivity> activities() {
        return EnumSet.copyOf(activities);
    }

    public CompanyStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
