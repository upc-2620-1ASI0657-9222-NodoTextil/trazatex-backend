package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.FailureStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "quality", name = "quality_failures")
class FailureJpaEntity {

    @Id
    private UUID id;
    private UUID qualityControlId;
    private UUID batchId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            schema = "quality",
            name = "quality_failure_tests",
            joinColumns = @JoinColumn(name = "failure_id")
    )
    private List<UUID> failedTestIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private FailureStatus status;

    private String cause;
    private BigDecimal affectedQuantityKg;
    private String observations;
    private LocalDateTime createdAt;

    protected FailureJpaEntity() {
    }

    static FailureJpaEntity fromDomain(Failure failure) {
        FailureJpaEntity entity = new FailureJpaEntity();
        entity.id = failure.getId();
        entity.qualityControlId = failure.getQualityControlId();
        entity.batchId = failure.getBatchId();
        entity.failedTestIds = new ArrayList<>(failure.getFailedTestIds());
        entity.status = failure.getStatus();
        entity.cause = failure.getCause();
        entity.affectedQuantityKg = failure.getAffectedQuantityKg();
        entity.observations = failure.getObservations();
        entity.createdAt = failure.getCreatedAt();
        return entity;
    }

    Failure toDomain() {
        return Failure.restore(
                id,
                qualityControlId,
                batchId,
                failedTestIds,
                status,
                cause,
                affectedQuantityKg,
                observations,
                createdAt
        );
    }
}
