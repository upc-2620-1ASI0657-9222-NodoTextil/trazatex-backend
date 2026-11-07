package com.nodotextil.trazatex.organizationaccess.infrastructure.persistence;

import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    UUID id;
    @Column(name = "company_id")
    UUID companyId;
    @Column(nullable = false)
    String email;
    @Column(name = "first_name", nullable = false)
    String firstName;
    @Column(name = "last_name", nullable = false)
    String lastName;
    String position;
    @Column(name = "password_hash", nullable = false)
    String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UserStatus status;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected UserEntity() {
    }
}
