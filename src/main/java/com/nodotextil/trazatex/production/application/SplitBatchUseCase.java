package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.transaction.annotation.Transactional;

public class SplitBatchUseCase {

    private static final int MAX_IDENTIFIER_GENERATION_ATTEMPTS = 100;

    private final BatchRepository batchRepository;
    private final BatchEventPublisher eventPublisher;
    private final Clock clock;

    public SplitBatchUseCase(
            BatchRepository batchRepository,
            BatchEventPublisher eventPublisher,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Result execute(UUID parentBatchId, List<BigDecimal> childQuantitiesKg) {
        Batch parent = batchRepository.findById(parentBatchId)
                .orElseThrow(() -> new BatchNotFoundException(parentBatchId));
        return execute(parentBatchId, parent.responsibleCompanyId(), childQuantitiesKg);
    }

    @Transactional
    public Result execute(UUID parentBatchId, UUID companyId, List<BigDecimal> childQuantitiesKg) {
        validateChildQuantities(childQuantitiesKg);

        Batch parent = batchRepository.findById(parentBatchId)
                .orElseThrow(() -> new BatchNotFoundException(parentBatchId));
        validateParent(parent);
        if (!parent.responsibleCompanyId().equals(companyId)) {
            throw new InvalidBatchException("Batch belongs to another company");
        }
        validateExactTotal(parent.quantityKg(), childQuantitiesKg);

        LocalDateTime occurredAt = LocalDateTime.now(clock);
        Set<UUID> allocatedIds = new HashSet<>();
        Set<String> allocatedTraceabilityIds = new HashSet<>();
        Set<String> allocatedQrCodes = new HashSet<>();

        List<Batch> children = childQuantitiesKg.stream()
                .map(quantity -> Batch.register(
                        generateUniqueUuid(allocatedIds),
                        generateUniqueIdentifier(
                                "TRZ-",
                                batchRepository::existsByTraceabilityId,
                                allocatedTraceabilityIds),
                        generateUniqueIdentifier(
                                "QR-", batchRepository::existsByQrCode, allocatedQrCodes),
                        parent.responsibleCompanyId(),
                        parent.supplierName(),
                        parent.geographicOrigin(),
                        parent.materialType(),
                        quantity,
                        parent.composition(),
                        occurredAt,
                        parent.receptionCharacteristics()))
                .toList();

        parent.markAsSplit();
        List<Batch> batchesToSave = new ArrayList<>(children.size() + 1);
        batchesToSave.add(parent);
        batchesToSave.addAll(children);
        List<Batch> savedBatches = batchRepository.saveAll(batchesToSave);

        Batch savedParent = savedBatches.getFirst();
        List<Batch> savedChildren = List.copyOf(savedBatches.subList(1, savedBatches.size()));
        eventPublisher.publish(new BatchSplitEvent(
                savedParent.id(),
                savedChildren.stream().map(Batch::id).toList(),
                occurredAt));

        return new Result(savedParent, savedChildren);
    }

    private static void validateChildQuantities(List<BigDecimal> childQuantitiesKg) {
        if (childQuantitiesKg == null || childQuantitiesKg.size() < 2) {
            throw new InvalidBatchException("A batch split requires at least two children");
        }
        if (childQuantitiesKg.stream()
                .anyMatch(quantity -> quantity == null
                        || quantity.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new InvalidBatchException("Every child quantity must be greater than zero");
        }
    }

    private static void validateParent(Batch parent) {
        if (!parent.isOperationallyEligible()) {
            throw new InvalidBatchException(
                    "Only AVAILABLE, non-final, unblocked batches can be split");
        }
    }

    private static void validateExactTotal(
            BigDecimal parentQuantityKg,
            List<BigDecimal> childQuantitiesKg) {
        BigDecimal total = childQuantitiesKg.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(parentQuantityKg) != 0) {
            throw new InvalidBatchException(
                    "Child quantities must add up exactly to the parent quantity");
        }
    }

    private static UUID generateUniqueUuid(Set<UUID> allocatedIds) {
        for (int attempt = 0; attempt < MAX_IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            UUID candidate = UUID.randomUUID();
            if (allocatedIds.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique batch id");
    }

    private static String generateUniqueIdentifier(
            String prefix,
            Predicate<String> alreadyExists,
            Set<String> allocatedIdentifiers) {
        for (int attempt = 0; attempt < MAX_IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = prefix + UUID.randomUUID();
            if (!alreadyExists.test(candidate) && allocatedIdentifiers.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique batch identifier");
    }

    public record Result(Batch parent, List<Batch> children) {

        public Result {
            children = List.copyOf(children);
        }
    }
}
