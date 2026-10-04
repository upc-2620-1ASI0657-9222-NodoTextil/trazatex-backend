package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.production.application.event.TransformationEventPublisher;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.CompositionCalculator;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
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

public class CompleteTransformationUseCase {

    private static final int MAX_IDENTIFIER_GENERATION_ATTEMPTS = 100;

    private final TransformationRepository transformationRepository;
    private final BatchRepository batchRepository;
    private final TransformationEventPublisher eventPublisher;
    private final Clock clock;

    public CompleteTransformationUseCase(
            TransformationRepository transformationRepository,
            BatchRepository batchRepository,
            TransformationEventPublisher eventPublisher,
            Clock clock) {
        this.transformationRepository = transformationRepository;
        this.batchRepository = batchRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Result execute(UUID transformationId, Command command) {
        Transformation transformation = transformationRepository.findById(transformationId)
                .orElseThrow(() -> new TransformationNotFoundException(transformationId));
        if (transformation.isCompleted()) {
            throw new InvalidTransformationException("Transformation is already completed");
        }
        validateOutputs(command.outputs());

        List<Batch> inputs = transformation.inputBatchIds().stream()
                .map(this::findBatch)
                .toList();
        inputs.forEach(CompleteTransformationUseCase::validateInputInTransformation);

        LocalDateTime completedAt = LocalDateTime.now(clock);
        List<CompositionComponent> outputComposition =
                CompositionCalculator.weightedByQuantity(inputs);
        String supplierName = inputs.size() == 1 ? inputs.getFirst().supplierName() : null;
        Set<UUID> allocatedIds = new HashSet<>();
        Set<String> allocatedTraceabilityIds = new HashSet<>();
        Set<String> allocatedQrCodes = new HashSet<>();

        List<Batch> outputs = command.outputs().stream()
                .map(output -> Batch.register(
                        generateUniqueUuid(allocatedIds),
                        generateUniqueIdentifier(
                                "TRZ-",
                                batchRepository::existsByTraceabilityId,
                                allocatedTraceabilityIds),
                        generateUniqueIdentifier(
                                "QR-", batchRepository::existsByQrCode, allocatedQrCodes),
                        transformation.companyId(),
                        supplierName,
                        output.geographicOrigin(),
                        output.materialType(),
                        output.quantityKg(),
                        outputComposition,
                        completedAt,
                        output.receptionCharacteristics()))
                .toList();

        inputs.forEach(Batch::markAsProcessed);
        transformation.complete(outputs.stream().map(Batch::id).toList(), completedAt);

        List<Batch> batchesToSave = new ArrayList<>(inputs.size() + outputs.size());
        batchesToSave.addAll(inputs);
        batchesToSave.addAll(outputs);
        List<Batch> savedBatches = batchRepository.saveAll(batchesToSave);
        List<Batch> savedOutputs = List.copyOf(
                savedBatches.subList(inputs.size(), savedBatches.size()));
        Transformation savedTransformation = transformationRepository.save(transformation);

        eventPublisher.publish(new TransformationCompletedEvent(
                savedTransformation.inputBatchIds(),
                savedTransformation.outputBatchIds(),
                completedAt));
        return new Result(savedTransformation, savedOutputs);
    }

    private Batch findBatch(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
    }

    private static void validateOutputs(List<OutputCommand> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            throw new InvalidTransformationException(
                    "A transformation requires at least one output batch");
        }
        if (outputs.stream().anyMatch(output -> output == null
                || output.quantityKg() == null
                || output.quantityKg().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new InvalidTransformationException(
                    "Every output quantity must be greater than zero");
        }
    }

    static void validateInputInTransformation(Batch input) {
        if (input.operationalPhase() != OperationalPhase.IN_TRANSFORMATION) {
            throw new InvalidTransformationException(
                    "All input batches must be IN_TRANSFORMATION");
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

    public record Command(List<OutputCommand> outputs) {
    }

    public record OutputCommand(
            BigDecimal quantityKg,
            MaterialType materialType,
            String geographicOrigin,
            String receptionCharacteristics) {
    }

    public record Result(Transformation transformation, List<Batch> outputs) {

        public Result {
            outputs = List.copyOf(outputs);
        }
    }
}
