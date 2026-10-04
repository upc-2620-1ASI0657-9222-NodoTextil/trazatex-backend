package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.application.event.BatchRegisteredEvent;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class RegisterBatchUseCase {

    private static final int MAX_IDENTIFIER_GENERATION_ATTEMPTS = 100;

    private final BatchRepository batchRepository;
    private final BatchEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterBatchUseCase(
            BatchRepository batchRepository,
            BatchEventPublisher eventPublisher,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public RegisterBatchUseCase(BatchRepository batchRepository, Clock clock) {
        this(batchRepository, new BatchEventPublisher() {
            @Override
            public void publish(BatchRegisteredEvent event) {
            }

            @Override
            public void publish(com.nodotextil.trazatex.production.application.event.BatchSplitEvent event) {
            }
        }, clock);
    }

    @Transactional
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
        Batch saved = batchRepository.save(batch);
        eventPublisher.publish(new BatchRegisteredEvent(saved.id(), saved.registeredAt()));
        return saved;
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
