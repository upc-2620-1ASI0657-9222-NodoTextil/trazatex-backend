package com.nodotextil.trazatex.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shared_audit_records")
class AuditRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(length = 4000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected AuditRecordJpaEntity() {
    }

    AuditRecordJpaEntity(AuditRecord record) {
        this.id = record.id();
        this.userId = record.userId();
        this.companyId = record.companyId();
        this.action = record.action();
        this.entityType = record.entityType();
        this.entityId = record.entityId();
        this.details = record.details();
        this.occurredAt = record.occurredAt();
    }

    AuditRecord toRecord() {
        return new AuditRecord(id, userId, companyId, action, entityType, entityId, details, occurredAt);
    }

    UUID getCompanyId() {
        return companyId;
    }

    LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
