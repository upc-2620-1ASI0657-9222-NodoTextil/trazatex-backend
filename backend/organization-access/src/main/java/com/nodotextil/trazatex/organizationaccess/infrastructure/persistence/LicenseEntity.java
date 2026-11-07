package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "license")
class LicenseEntity {

    @Id
    UUID id;
    @Column(name = "max_companies", nullable = false)
    int maxCompanies;
    @Column(name = "max_users_per_company", nullable = false)
    int maxUsersPerCompany;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected LicenseEntity() {
    }
}
