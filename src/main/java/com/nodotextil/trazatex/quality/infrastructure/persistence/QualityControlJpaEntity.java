package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.ControlType;
import com.nodotextil.trazatex.quality.domain.QualityControl;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quality_controls")
class QualityControlJpaEntity {

    @Id
    private UUID id;
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    private ControlType type;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "quality_control_tests",
            joinColumns = @JoinColumn(name = "control_id")
    )
    @OrderColumn(name = "test_order")
    private List<QualityTestEmbeddable> tests = new ArrayList<>();

    protected QualityControlJpaEntity() {
    }

    static QualityControlJpaEntity fromDomain(QualityControl control) {
        QualityControlJpaEntity entity = new QualityControlJpaEntity();
        entity.id = control.getId();
        entity.batchId = control.getBatchId();
        entity.type = control.getType();
        entity.startedAt = control.getStartedAt();
        entity.completedAt = control.getCompletedAt();
        entity.tests = control.getTests().stream()
                .map(QualityTestEmbeddable::fromDomain)
                .toList();
        return entity;
    }

    QualityControl toDomain() {
        return QualityControl.restore(
                id,
                batchId,
                type,
                startedAt,
                completedAt,
                tests.stream().map(QualityTestEmbeddable::toDomain).toList()
        );
    }
}
