package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transformations")
class TransformationEntity {

    @Id
    UUID id;
    @Column(name = "company_id", nullable = false, updatable = false)
    UUID companyId;
    @Enumerated(EnumType.STRING)
    @Column(name = "transformation_type", nullable = false, updatable = false)
    TransformationType type;
    @Column(name = "machinery_id", nullable = false, updatable = false)
    UUID machineId;
    @Column(name = "operator_id", nullable = false, updatable = false)
    UUID operatorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TransformationStatus status;
    @Column(name = "total_input_kg", nullable = false, updatable = false)
    BigDecimal totalInputKg;
    @Column(name = "total_output_kg")
    BigDecimal totalOutputKg;
    @Column(name = "waste_kg")
    BigDecimal wasteKg;
    @Column(name = "waste_reason")
    String wasteReason;
    @Column(name = "shrinkage_kg")
    BigDecimal shrinkageKg;
    @Column(name = "start_time", nullable = false, updatable = false)
    Instant startTime;
    @Column(name = "end_time")
    Instant endTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "transformation_inputs", joinColumns = @JoinColumn(name = "transformation_id"))
    @OrderColumn(name = "position")
    List<InputRow> inputs = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "transformation_outputs", joinColumns = @JoinColumn(name = "transformation_id"))
    @OrderColumn(name = "position")
    @Column(name = "batch_id", nullable = false)
    List<UUID> outputBatchIds = new ArrayList<>();

    protected TransformationEntity() {
    }
}
