package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitations")
class InvitationEntity {

    @Id
    UUID id;
    @Column(nullable = false)
    String token;
    @Column(nullable = false)
    String email;
    @Column(name = "display_name")
    String displayName;
    @Column(name = "first_name")
    String firstName;
    @Column(name = "last_name")
    String lastName;
    String position;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;
    @Column(name = "company_id")
    UUID companyId;
    @Column(name = "issued_by")
    UUID issuedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    InvitationStatus status;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected InvitationEntity() {
    }
}
