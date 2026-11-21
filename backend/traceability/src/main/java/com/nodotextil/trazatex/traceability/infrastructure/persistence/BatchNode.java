package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Node("Batch")
public class BatchNode {

    @Id
    @Property("batchId")
    private UUID batchId;
    private String traceabilityId;
    private String qrCode;
    private UUID companyId;
    private String materialType;
    private BatchPhase phase;
    private QualityStatus qualityStatus;
    private Boolean qualityHold;
    private Boolean finalProduct;
    private Instant createdAt;
    private String supplierName;
    private String origin;
    private BigDecimal quantityKg;
    private String composition;
    private String receptionCharacteristics;
    private boolean provisional;
    private Long batchVersion;

    protected BatchNode() {
    }

    public BatchNode(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }

    public String traceabilityId() {
        return traceabilityId;
    }

    public String qrCode() {
        return qrCode;
    }

    public UUID companyId() {
        return companyId;
    }

    public String materialType() {
        return materialType;
    }

    public BatchPhase phase() {
        return phase;
    }

    public QualityStatus qualityStatus() {
        return qualityStatus;
    }

    public Boolean qualityHold() {
        return qualityHold;
    }

    public Boolean finalProduct() {
        return finalProduct;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String supplierName() {
        return supplierName;
    }

    public String origin() {
        return origin;
    }

    public BigDecimal quantityKg() {
        return quantityKg;
    }

    public String composition() {
        return composition;
    }

    public String receptionCharacteristics() {
        return receptionCharacteristics;
    }

    public boolean provisional() {
        return provisional;
    }

    public Long batchVersion() {
        return batchVersion;
    }
}
