package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_requests")
class AdminRequestEntity {

    @Id
    UUID id;
    @Column(name = "company_id", nullable = false)
    UUID companyId;
    @Column(name = "requested_by", nullable = false)
    UUID requestedBy;
    @Column(name = "full_name", nullable = false)
    String fullName;
    @Column(nullable = false)
    String email;
    @Column(nullable = false)
    String position;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AdminRequestStatus status;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "resolved_at")
    Instant resolvedAt;
    @Column(name = "resolved_by")
    UUID resolvedBy;

    protected AdminRequestEntity() {
    }
}
