package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.MachineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "production_machines")
class MachineJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "internal_code", nullable = false)
    private String internalCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MachineStatus status;

    protected MachineJpaEntity() {
    }

    MachineJpaEntity(
            UUID id,
            String internalCode,
            String name,
            String type,
            UUID companyId,
            MachineStatus status) {
        this.id = id;
        this.internalCode = internalCode;
        this.name = name;
        this.type = type;
        this.companyId = companyId;
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    String getInternalCode() {
        return internalCode;
    }

    String getName() {
        return name;
    }

    String getType() {
        return type;
    }

    UUID getCompanyId() {
        return companyId;
    }

    MachineStatus getStatus() {
        return status;
    }
}
