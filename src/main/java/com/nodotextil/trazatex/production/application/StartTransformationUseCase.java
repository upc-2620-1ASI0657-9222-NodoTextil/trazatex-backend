package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import com.nodotextil.trazatex.production.domain.MachineStatus;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class StartTransformationUseCase {

    private final TransformationRepository transformationRepository;
    private final BatchRepository batchRepository;
    private final MachineRepository machineRepository;
    private final Clock clock;

    public StartTransformationUseCase(
            TransformationRepository transformationRepository,
            BatchRepository batchRepository,
            MachineRepository machineRepository,
            Clock clock) {
        this.transformationRepository = transformationRepository;
        this.batchRepository = batchRepository;
        this.machineRepository = machineRepository;
        this.clock = clock;
    }

    @Transactional
    public Transformation execute(Command command) {
        validateInputIds(command.inputBatchIds());

        Machine machine = machineRepository.findById(command.machineId())
                .orElseThrow(() -> new MachineNotFoundException(command.machineId()));
        if (machine.status() != MachineStatus.ACTIVE) {
            throw new InvalidTransformationException(
                    "Only an ACTIVE machine can be used in a transformation");
        }

        List<Batch> inputs = command.inputBatchIds().stream()
                .map(this::findInput)
                .toList();
        inputs.forEach(StartTransformationUseCase::validateAvailableInput);
        BigDecimal totalInputKg = inputs.stream()
                .map(Batch::quantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startedAt = LocalDateTime.now(clock);
        Transformation transformation = Transformation.start(
                UUID.randomUUID(),
                command.companyId(),
                command.operatorId(),
                command.machineId(),
                command.type(),
                command.inputBatchIds(),
                startedAt,
                totalInputKg);

        inputs.forEach(Batch::startTransformation);
        batchRepository.saveAll(inputs);
        return transformationRepository.save(transformation);
    }

    private static void validateInputIds(List<UUID> inputBatchIds) {
        if (inputBatchIds == null || inputBatchIds.isEmpty()) {
            throw new InvalidTransformationException(
                    "A transformation requires at least one input batch");
        }
        if (inputBatchIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidTransformationException("Input batch id is required");
        }
        if (new HashSet<>(inputBatchIds).size() != inputBatchIds.size()) {
            throw new InvalidTransformationException("Input batches cannot be duplicated");
        }
    }

    private Batch findInput(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
    }

    static void validateAvailableInput(Batch batch) {
        if (batch.operationalPhase() != OperationalPhase.AVAILABLE) {
            throw new InvalidTransformationException("All input batches must be AVAILABLE");
        }
    }

    public record Command(
            UUID companyId,
            UUID operatorId,
            UUID machineId,
            TransformationType type,
            List<UUID> inputBatchIds) {
    }
}
