package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.BatchQuality;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "quality_batch_states")
class BatchQualityJpaEntity {

    @Id
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    private QualityStatus status;

    protected BatchQualityJpaEntity() {
    }

    static BatchQualityJpaEntity fromDomain(BatchQuality quality) {
        BatchQualityJpaEntity entity = new BatchQualityJpaEntity();
        entity.batchId = quality.getBatchId();
        entity.status = quality.getStatus();
        return entity;
    }

    BatchQuality toDomain() {
        return new BatchQuality(batchId, status);
    }
}
