package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code (:Batch)}. Nodo plano, para leer: las relaciones (Figura 40) se crean y recorren con Cypher y las escrituras de los
 * consumidores van por {@code MERGE}, no por {@code save()} (que lee y luego escribe, y pisaría datos nuevos con valores viejos).
 *
 * <p>Siempre compartidos: {@code batchId}, {@code traceabilityId}, {@code qrCode}, {@code companyId}, {@code materialType},
 * {@code phase}, {@code qualityStatus}, {@code createdAt}, {@code finalProduct}. Configurables: {@code supplierName},
 * {@code origin}, {@code quantityKg}, {@code composition} (JSON), {@code receptionCharacteristics}.
 *
 * <p>{@code provisional}: se creó por un evento que llegó antes que {@code BatchRegistered} y aún no tiene sus datos.
 * {@code batchVersion}: la del último {@code BatchPhaseChanged} aplicado (fase, retención y dueño solo cambian con una
 * mayor); {@code null} = ninguno todavía. Un nodo provisional puede tener casi todo en {@code null}.
 */
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

    /** JSON {@code [{"name": ..., "percentage": ...}]}: una propiedad de Neo4j no puede ser una lista de mapas. */
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
