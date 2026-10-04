package com.nodotextil.trazatex.production.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Batch {

    private static final BigDecimal TOTAL_COMPOSITION_PERCENTAGE = new BigDecimal("100");

    private final UUID id;
    private final String traceabilityId;
    private final String qrCode;
    private UUID responsibleCompanyId;
    private final String supplierName;
    private final String geographicOrigin;
    private final MaterialType materialType;
    private final BigDecimal quantityKg;
    private final List<CompositionComponent> composition;
    private OperationalPhase operationalPhase;
    private final LocalDateTime registeredAt;
    private final String receptionCharacteristics;
    private boolean finalProduct;
    private String buyerOrDistributor;
    private BigDecimal price;
    private String currency;
    private LocalDate commercialDate;
    private String commercialReference;
    private boolean operationallyBlocked;

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
            String receptionCharacteristics,
            boolean finalProduct,
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference,
            boolean operationallyBlocked) {
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
        validateCommercialData(
                finalProduct,
                buyerOrDistributor,
                price,
                currency,
                commercialDate,
                commercialReference);
        this.finalProduct = finalProduct;
        this.buyerOrDistributor = buyerOrDistributor;
        this.price = price;
        this.currency = currency;
        this.commercialDate = commercialDate;
        this.commercialReference = commercialReference;
        this.operationallyBlocked = operationallyBlocked;
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
                receptionCharacteristics,
                false,
                null,
                null,
                null,
                null,
                null,
                false);
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
            String receptionCharacteristics,
            boolean finalProduct,
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference) {
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
                receptionCharacteristics,
                finalProduct,
                buyerOrDistributor,
                price,
                currency,
                commercialDate,
                commercialReference,
                false);
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
        return reconstitute(
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
                receptionCharacteristics,
                false,
                null,
                null,
                null,
                null,
                null,
                false);
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
            String receptionCharacteristics,
            boolean finalProduct,
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference) {
        return reconstitute(
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
                receptionCharacteristics,
                finalProduct,
                buyerOrDistributor,
                price,
                currency,
                commercialDate,
                commercialReference,
                false);
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
            String receptionCharacteristics,
            boolean finalProduct,
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference,
            boolean operationallyBlocked) {
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
                receptionCharacteristics,
                finalProduct,
                buyerOrDistributor,
                price,
                currency,
                commercialDate,
                commercialReference,
                operationallyBlocked);
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

    private static void validateCommercialData(
            boolean finalProduct,
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference) {
        if (!finalProduct && (buyerOrDistributor != null
                || price != null
                || currency != null
                || commercialDate != null
                || commercialReference != null)) {
            throw new InvalidBatchException(
                    "Commercial data is only allowed for a final product");
        }
        if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidBatchException("Price cannot be negative");
        }
        if (price != null && (currency == null || currency.isBlank())) {
            throw new InvalidBatchException("Currency is required when price is provided");
        }
    }

    public void markAsFinalProduct(
            String buyerOrDistributor,
            BigDecimal price,
            String currency,
            LocalDate commercialDate,
            String commercialReference) {
        ensureOperationallyEligible();
        validateCommercialData(true, buyerOrDistributor, price, currency, commercialDate, commercialReference);
        this.finalProduct = true;
        this.buyerOrDistributor = buyerOrDistributor;
        this.price = price;
        this.currency = currency;
        this.commercialDate = commercialDate;
        this.commercialReference = commercialReference;
    }

    public void markAsSplit() {
        ensureOperationallyEligible();
        operationalPhase = OperationalPhase.SPLIT;
    }

    public void startTransformation() {
        ensureOperationallyEligible();
        operationalPhase = OperationalPhase.IN_TRANSFORMATION;
    }

    public void startTransfer() {
        ensureOperationallyEligible();
        operationalPhase = OperationalPhase.IN_TRANSFER;
    }

    public void acceptTransfer(UUID destinationCompanyId) {
        ensureInTransfer();
        responsibleCompanyId = Objects.requireNonNull(
                destinationCompanyId, "Destination company id is required");
        operationalPhase = OperationalPhase.AVAILABLE;
    }

    public void rejectTransfer() {
        ensureInTransfer();
        operationalPhase = OperationalPhase.AVAILABLE;
    }

    public boolean isOperationallyEligible() {
        return operationalPhase == OperationalPhase.AVAILABLE
                && !finalProduct
                && !operationallyBlocked;
    }


    public void startQualityControl() {
        if (operationalPhase != OperationalPhase.AVAILABLE) {
            throw new InvalidBatchException("Only an AVAILABLE batch can enter quality control");
        }
        operationalPhase = OperationalPhase.IN_QUALITY_CONTROL;
    }

    public void finishQualityControl() {
        if (operationalPhase != OperationalPhase.IN_QUALITY_CONTROL) {
            throw new InvalidBatchException("Batch is not in quality control");
        }
        operationalPhase = OperationalPhase.AVAILABLE;
    }

    public void discard() {
        if (operationalPhase != OperationalPhase.AVAILABLE) {
            throw new InvalidBatchException("Only an AVAILABLE batch can be discarded");
        }
        operationalPhase = OperationalPhase.DISCARDED;
        operationallyBlocked = true;
    }

    public void block() {
        operationallyBlocked = true;
    }

    public void unblock() {
        operationallyBlocked = false;
    }

    private void ensureOperationallyEligible() {
        if (operationalPhase != OperationalPhase.AVAILABLE) {
            throw new InvalidBatchException("Only an AVAILABLE batch can be operated");
        }
        if (finalProduct) {
            throw new InvalidBatchException("A final product batch cannot be operated");
        }
        if (operationallyBlocked) {
            throw new InvalidBatchException("Batch is operationally blocked");
        }
    }

    private void ensureInTransfer() {
        if (operationalPhase != OperationalPhase.IN_TRANSFER) {
            throw new InvalidBatchException("Batch is not in transfer");
        }
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

    public boolean finalProduct() {
        return finalProduct;
    }

    public String buyerOrDistributor() {
        return buyerOrDistributor;
    }

    public BigDecimal price() {
        return price;
    }

    public String currency() {
        return currency;
    }

    public LocalDate commercialDate() {
        return commercialDate;
    }

    public String commercialReference() {
        return commercialReference;
    }

    public boolean operationallyBlocked() {
        return operationallyBlocked;
    }
}
