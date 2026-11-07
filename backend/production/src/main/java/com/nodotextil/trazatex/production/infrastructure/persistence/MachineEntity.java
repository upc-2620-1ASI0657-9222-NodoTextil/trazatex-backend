package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machines")
class MachineEntity {

    @Id
    UUID id;
    @Column(name = "company_id", nullable = false, updatable = false)
    UUID companyId;
    @Column(name = "internal_code", nullable = false, updatable = false)
    String internalCode;
    @Column(nullable = false, updatable = false)
    String name;
    @Column(nullable = false, updatable = false)
    String type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MachineStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected MachineEntity() {
    }
}
