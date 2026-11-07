package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.model.BatchOriginType;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tabla {@code batches}. {@code version} es el bloqueo optimista: lo incrementa Hibernate en cada UPDATE. */
@Entity
@Table(name = "batches")
class BatchEntity {

    @Id
    UUID id;
    @Version
    Long version;
    @Column(name = "traceability_id", nullable = false, updatable = false)
    String traceabilityId;
    @Column(name = "qr_code", nullable = false, updatable = false)
    String qrCode;
    @Column(name = "company_id", nullable = false)
    UUID companyId;
    @Column(nullable = false, updatable = false)
    String name;
    @Column(name = "material_type", nullable = false, updatable = false)
    String materialType;
    @Column(name = "quantity_kg", nullable = false, updatable = false)
    BigDecimal quantityKg;
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    BatchPhase phase;
    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    QualityStatus qualityStatus;
    @Column(name = "quality_hold", nullable = false)
    boolean qualityHold;
    @Column(name = "final_product", nullable = false)
    boolean finalProduct;
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, updatable = false)
    BatchOriginType originType;
    @Column(updatable = false)
    String origin;
    @Column(name = "supplier_name", updatable = false)
    String supplierName;
    @Column(name = "reception_characteristics", updatable = false)
    String receptionCharacteristics;
    @Column(name = "created_by", nullable = false, updatable = false)
    UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "buyer_or_distributor")
    String buyerOrDistributor;
    @Column(name = "sale_price")
    BigDecimal salePrice;
    @Column
    String currency;
    @Column(name = "commercial_exit_date")
    LocalDate commercialExitDate;
    @Column(name = "commercial_reference")
    String commercialReference;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "batch_compositions", joinColumns = @JoinColumn(name = "batch_id"))
    @OrderColumn(name = "position")
    List<CompositionRow> composition = new ArrayList<>();

    protected BatchEntity() {
    }
}
