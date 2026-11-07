package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.organizationaccess.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Licencia única creada por el bootstrap (ADR-013). Fija los límites de la cadena (RF-013). Los conteos
 * ({@code companies}, {@code usedSlots}) los entrega quien llama: la persistencia los calcula con la fila de la
 * licencia (o de la empresa) bloqueada para que dos operaciones simultáneas no superen el límite.
 */
public final class License {

    private final UUID id;
    private final int maxCompanies;
    private final int maxUsersPerCompany;
    private final Instant createdAt;

    private License(UUID id, int maxCompanies, int maxUsersPerCompany, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (maxCompanies < 1 || maxUsersPerCompany < 1) {
            throw new ValidationException("Los límites de la licencia deben ser al menos 1.");
        }
        this.maxCompanies = maxCompanies;
        this.maxUsersPerCompany = maxUsersPerCompany;
    }

    public static License create(UUID id, int maxCompanies, int maxUsersPerCompany, Instant now) {
        return new License(id, maxCompanies, maxUsersPerCompany, now);
    }

    public static License restore(UUID id, int maxCompanies, int maxUsersPerCompany, Instant createdAt) {
        return new License(id, maxCompanies, maxUsersPerCompany, createdAt);
    }

    /** Las empresas inactivas también cuentan (US02 esc. 2). */
    public boolean hasCompanyCapacity(long companies) {
        return companies < maxCompanies;
    }

    public void ensureCanCreateCompany(long companies) {
        if (!hasCompanyCapacity(companies)) {
            throw new ConflictException(ErrorCodes.COMPANY_LIMIT_REACHED,
                    "Se alcanzó el límite de empresas contratado (" + maxCompanies + ").");
        }
    }

    /** {@code usedSlots} = usuarios ACTIVE + invitaciones PENDING vigentes de la empresa. */
    public boolean hasUserCapacity(long usedSlots) {
        return usedSlots < maxUsersPerCompany;
    }

    public void ensureUserCapacity(long usedSlots) {
        if (!hasUserCapacity(usedSlots)) {
            throw new ConflictException(ErrorCodes.USER_LIMIT_REACHED,
                    "No existe capacidad de usuarios disponible (límite " + maxUsersPerCompany + " por empresa).");
        }
    }

    public LicenseUsage usage(long companies, List<LicenseUsage.CompanyUsage> perCompany) {
        return new LicenseUsage(maxCompanies, maxUsersPerCompany, companies, perCompany);
    }

    public UUID id() {
        return id;
    }

    public int maxCompanies() {
        return maxCompanies;
    }

    public int maxUsersPerCompany() {
        return maxUsersPerCompany;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
