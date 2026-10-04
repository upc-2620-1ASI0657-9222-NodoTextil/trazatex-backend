package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "production_batches",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_production_batches_traceability_id",
                    columnNames = "traceability_id"),
            @UniqueConstraint(
                    name = "uk_production_batches_qr_code",
                    columnNames = "qr_code")
        })
class BatchJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "traceability_id", nullable = false, updatable = false)
    private String traceabilityId;

    @Column(name = "qr_code", nullable = false, updatable = false)
    private String qrCode;

    @Column(name = "responsible_company_id", nullable = false, updatable = false)
    private UUID responsibleCompanyId;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "geographic_origin", nullable = false)
    private String geographicOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false)
    private MaterialType materialType;

    @Column(name = "quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityKg;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "production_batch_composition",
            joinColumns = @JoinColumn(name = "batch_id", nullable = false))
    @OrderColumn(name = "component_order")
    private List<CompositionComponentJpaEmbeddable> composition = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_phase", nullable = false)
    private OperationalPhase operationalPhase;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "reception_characteristics", nullable = false, length = 2000)
    private String receptionCharacteristics;

    protected BatchJpaEntity() {
    }

    BatchJpaEntity(
            UUID id,
            String traceabilityId,
            String qrCode,
            UUID responsibleCompanyId,
            String supplierName,
            String geographicOrigin,
            MaterialType materialType,
            BigDecimal quantityKg,
            List<CompositionComponentJpaEmbeddable> composition,
            OperationalPhase operationalPhase,
            LocalDateTime registeredAt,
            String receptionCharacteristics) {
        this.id = id;
        this.traceabilityId = traceabilityId;
        this.qrCode = qrCode;
        this.responsibleCompanyId = responsibleCompanyId;
        this.supplierName = supplierName;
        this.geographicOrigin = geographicOrigin;
        this.materialType = materialType;
        this.quantityKg = quantityKg;
        this.composition = new ArrayList<>(composition);
        this.operationalPhase = operationalPhase;
        this.registeredAt = registeredAt;
        this.receptionCharacteristics = receptionCharacteristics;
    }

    UUID getId() {
        return id;
    }

    String getTraceabilityId() {
        return traceabilityId;
    }

    String getQrCode() {
        return qrCode;
    }

    UUID getResponsibleCompanyId() {
        return responsibleCompanyId;
    }

    String getSupplierName() {
        return supplierName;
    }

    String getGeographicOrigin() {
        return geographicOrigin;
    }

    MaterialType getMaterialType() {
        return materialType;
    }

    BigDecimal getQuantityKg() {
        return quantityKg;
    }

    List<CompositionComponentJpaEmbeddable> getComposition() {
        return composition;
    }

    OperationalPhase getOperationalPhase() {
        return operationalPhase;
    }

    LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    String getReceptionCharacteristics() {
        return receptionCharacteristics;
    }
}
