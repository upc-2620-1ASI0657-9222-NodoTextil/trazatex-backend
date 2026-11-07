package com.nodotextil.trazatex.traceability.infrastructure.persistence;

import com.nodotextil.trazatex.traceability.domain.model.TransformationType;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code (:Transformation)}. Nodo plano, para leer (ver {@link BatchNode}). {@code companyId} se agrega a las propiedades
 * de {@code traceability.md}: el filtro B2B decide por la empresa de cada nodo y la transformación pertenece a una.
 * Configurables: {@code machineName} y {@code machineType} (campo {@code MACHINERY}, foto del momento; el
 * {@code machineId} no se guarda: otra empresa no puede consultarlo), {@code shrinkageKg}, {@code wasteKg}, {@code wasteReason}.
 */
@Node("Transformation")
public class TransformationNode {

    @Id
    @Property("transformationId")
    private UUID transformationId;
    private UUID companyId;
    private TransformationType type;
    private Instant startedAt;
    private Instant completedAt;
    private String machineName;
    private String machineType;
    private BigDecimal shrinkageKg;
    private BigDecimal wasteKg;
    private String wasteReason;
    private boolean provisional;

    protected TransformationNode() {
    }

    public TransformationNode(UUID transformationId) {
        this.transformationId = transformationId;
    }

    public UUID transformationId() {
        return transformationId;
    }

    public UUID companyId() {
        return companyId;
    }

    public TransformationType type() {
        return type;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String machineName() {
        return machineName;
    }

    public String machineType() {
        return machineType;
    }

    public BigDecimal shrinkageKg() {
        return shrinkageKg;
    }

    public BigDecimal wasteKg() {
        return wasteKg;
    }

    public String wasteReason() {
        return wasteReason;
    }

    public boolean provisional() {
        return provisional;
    }
}
