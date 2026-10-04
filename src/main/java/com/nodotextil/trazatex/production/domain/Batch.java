package com.nodotextil.trazatex.production.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Batch {

    private static final BigDecimal TOTAL_COMPOSITION_PERCENTAGE = new BigDecimal("100");

    private final UUID id;
    private final String traceabilityId;
    private final String qrCode;
    private final UUID responsibleCompanyId;
    private final String supplierName;
    private final String geographicOrigin;
    private final MaterialType materialType;
    private final BigDecimal quantityKg;
    private final List<CompositionComponent> composition;
    private OperationalPhase operationalPhase;
    private final LocalDateTime registeredAt;
    private final String receptionCharacteristics;

    private Batch(
            UUID id,
            String traceabilityId,
            String qrCode,
            UUID responsibleCompanyId,
            String supplierName,
            String geographicOrigin,
            MaterialType materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            OperationalPhase operationalPhase,
            LocalDateTime registeredAt,
            String receptionCharacteristics) {
        this.id = Objects.requireNonNull(id, "Batch id is required");
        this.traceabilityId = requireText(traceabilityId, "Traceability id is required");
        this.qrCode = requireText(qrCode, "QR code is required");
        this.responsibleCompanyId = Objects.requireNonNull(
                responsibleCompanyId, "Responsible company id is required");
        this.supplierName = supplierName;
        this.geographicOrigin = requireText(geographicOrigin, "Geographic origin is required");
        this.materialType = Objects.requireNonNull(materialType, "Material type is required");
        this.quantityKg = validateQuantity(quantityKg);
        this.composition = validateComposition(composition);
        this.operationalPhase = Objects.requireNonNull(
                operationalPhase, "Operational phase is required");
        this.registeredAt = Objects.requireNonNull(registeredAt, "Registration date is required");
        this.receptionCharacteristics = requireText(
                receptionCharacteristics, "Reception characteristics are required");
    }

    public static Batch register(
            UUID id,
            String traceabilityId,
            String qrCode,
            UUID responsibleCompanyId,
            String supplierName,
            String geographicOrigin,
            MaterialType materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            LocalDateTime registeredAt,
            String receptionCharacteristics) {
        return new Batch(
                id,
                traceabilityId,
                qrCode,
                responsibleCompanyId,
                supplierName,
                geographicOrigin,
                materialType,
                quantityKg,
                composition,
                OperationalPhase.AVAILABLE,
                registeredAt,
                receptionCharacteristics);
    }

    public static Batch reconstitute(
            UUID id,
            String traceabilityId,
            String qrCode,
            UUID responsibleCompanyId,
            String supplierName,
            String geographicOrigin,
            MaterialType materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            OperationalPhase operationalPhase,
            LocalDateTime registeredAt,
            String receptionCharacteristics) {
        return new Batch(
                id,
                traceabilityId,
                qrCode,
                responsibleCompanyId,
                supplierName,
                geographicOrigin,
                materialType,
                quantityKg,
                composition,
                operationalPhase,
                registeredAt,
                receptionCharacteristics);
    }

    private static BigDecimal validateQuantity(BigDecimal quantityKg) {
        if (quantityKg == null || quantityKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBatchException("Quantity in kilograms must be greater than zero");
        }
        return quantityKg;
    }

    private static List<CompositionComponent> validateComposition(
            List<CompositionComponent> composition) {
        if (composition == null || composition.isEmpty()) {
            throw new InvalidBatchException("Composition must contain at least one component");
        }

        List<CompositionComponent> components = List.copyOf(composition);
        BigDecimal total = components.stream()
                .map(CompositionComponent::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(TOTAL_COMPOSITION_PERCENTAGE) != 0) {
            throw new InvalidBatchException("Composition percentages must add up to exactly 100");
        }
        return components;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidBatchException(message);
        }
        return value;
    }

    public void markAsSplit() {
        if (operationalPhase != OperationalPhase.AVAILABLE) {
            throw new InvalidBatchException("Only an AVAILABLE batch can be split");
        }
        operationalPhase = OperationalPhase.SPLIT;
    }

    public void startTransformation() {
        if (operationalPhase != OperationalPhase.AVAILABLE) {
            throw new InvalidBatchException(
                    "Only an AVAILABLE batch can enter a transformation");
        }
        operationalPhase = OperationalPhase.IN_TRANSFORMATION;
    }

    public void markAsProcessed() {
        if (operationalPhase != OperationalPhase.IN_TRANSFORMATION) {
            throw new InvalidBatchException(
                    "Only an IN_TRANSFORMATION batch can be processed");
        }
        operationalPhase = OperationalPhase.PROCESSED;
    }

    public UUID id() {
        return id;
    }

    public String traceabilityId() {
        return traceabilityId;
    }

    public String qrCode() {
        return qrCode;
    }

    public UUID responsibleCompanyId() {
        return responsibleCompanyId;
    }

    public String supplierName() {
        return supplierName;
    }

    public String geographicOrigin() {
        return geographicOrigin;
    }

    public MaterialType materialType() {
        return materialType;
    }

    public BigDecimal quantityKg() {
        return quantityKg;
    }

    public List<CompositionComponent> composition() {
        return composition;
    }

    public OperationalPhase operationalPhase() {
        return operationalPhase;
    }

    public LocalDateTime registeredAt() {
        return registeredAt;
    }

    public String receptionCharacteristics() {
        return receptionCharacteristics;
    }
}
