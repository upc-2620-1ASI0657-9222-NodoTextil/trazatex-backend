package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.traceability.application.dto.EventFields;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;
import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los payloads del catálogo (docs/architecture/communication.md) tal como llegan; solo los campos que traceability usa, y todo
 * lo demás se ignora (un campo nuevo de Production no rompe a este servicio). Los datos opcionales o ausentes llegan nulos y
 * el comando decide si son obligatorios.
 */
final class EventPayloads {

    private EventPayloads() {
    }

    /** {@code FailureConfirmed}: solo la falla y el lote fallado (causa, cantidad afectada y demás se ignoran). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FailureConfirmed(UUID failureId, UUID batchId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String name, BigDecimal percentage) {
    }

    /** {@code composition[]} como componentes del dominio; uno sin nombre o sin porcentaje invalida el evento. */
    static List<CompositionComponent> parts(List<Part> parts) {
        if (parts == null) {
            return null;
        }
        return parts.stream().map(part -> {
            if (part == null || part.name() == null || part.percentage() == null) {
                throw EventFields.invalid("composition", "trae un componente sin nombre o sin porcentaje");
            }
            return new CompositionComponent(part.name(), part.percentage());
        }).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchRegistered(UUID batchId, String traceabilityId, String qrCode, UUID companyId, String materialType, List<Part> composition,
                           BigDecimal quantityKg, String origin, String supplierName, String receptionCharacteristics, Instant createdAt, String name) {
    public BatchRegistered(UUID batchId, String traceabilityId, String qrCode, UUID companyId, String materialType, List<Part> composition,
                           BigDecimal quantityKg, String origin, String supplierName, String receptionCharacteristics, Instant createdAt) {
        this(batchId, traceabilityId, qrCode, companyId, materialType, composition, quantityKg, origin, supplierName, receptionCharacteristics, createdAt, null);
    }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SplitChild(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg, List<Part> composition,
                      String origin, String supplierName, String receptionCharacteristics, QualityStatus qualityStatus, Instant createdAt, String name) {
    public SplitChild(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg, List<Part> composition,
                      String origin, String supplierName, String receptionCharacteristics, QualityStatus qualityStatus, Instant createdAt) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, origin, supplierName, receptionCharacteristics, qualityStatus, createdAt, null);
    }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchSplit(UUID parentBatchId, UUID companyId, List<SplitChild> children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchPhaseChanged(UUID batchId, UUID companyId, BatchPhase newPhase, Boolean qualityHold, Long batchVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransformationInput(UUID batchId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransformationOutput(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                                List<Part> composition, Boolean finalProduct, String name) {
    public TransformationOutput(UUID batchId, String traceabilityId, String qrCode, String materialType, BigDecimal quantityKg,
                                List<Part> composition, Boolean finalProduct) {
        this(batchId, traceabilityId, qrCode, materialType, quantityKg, composition, finalProduct, null);
    }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransformationCompleted(UUID transformationId, TransformationType type, UUID companyId, String machineName, String machineType,
                                   List<TransformationInput> inputs, List<TransformationOutput> outputs, BigDecimal wasteKg, String wasteReason,
                                   BigDecimal shrinkageKg, Instant startedAt, Instant completedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransferAccepted(UUID transferId, List<UUID> batchIds, UUID toCompanyId, Instant acceptedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QualityStatusChanged(UUID batchId, QualityStatus newStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyRegistered(UUID companyId, String businessName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyPrivacyUpdated(UUID companyId, Map<String, String> fields) {
    }
}
