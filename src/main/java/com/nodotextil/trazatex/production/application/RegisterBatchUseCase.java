package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class RegisterBatchUseCase {

    private static final int MAX_IDENTIFIER_GENERATION_ATTEMPTS = 100;

    private final BatchRepository batchRepository;
    private final Clock clock;

    public RegisterBatchUseCase(BatchRepository batchRepository, Clock clock) {
        this.batchRepository = batchRepository;
        this.clock = clock;
    }

    public Batch execute(Command command) {
        Batch batch = Batch.register(
                UUID.randomUUID(),
                generateUniqueIdentifier("TRZ-", batchRepository::existsByTraceabilityId),
                generateUniqueIdentifier("QR-", batchRepository::existsByQrCode),
                command.responsibleCompanyId(),
                command.supplierName(),
                command.geographicOrigin(),
                command.materialType(),
                command.quantityKg(),
                command.composition(),
                LocalDateTime.now(clock),
                command.receptionCharacteristics());
        return batchRepository.save(batch);
    }

    private String generateUniqueIdentifier(String prefix, Predicate<String> alreadyExists) {
        for (int attempt = 0; attempt < MAX_IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = prefix + UUID.randomUUID();
            if (!alreadyExists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique batch identifier");
    }

    public record Command(
            UUID responsibleCompanyId,
            String supplierName,
            String geographicOrigin,
            MaterialType materialType,
            BigDecimal quantityKg,
            List<CompositionComponent> composition,
            String receptionCharacteristics) {
    }
}
