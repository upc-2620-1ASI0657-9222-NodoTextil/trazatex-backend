package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_projection")
class CompanyProjectionEntity {

    @Id
    @Column(name = "company_id")
    UUID companyId;
    @Column(name = "business_name")
    String businessName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CompanyStatus status;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected CompanyProjectionEntity() {
    }
}
