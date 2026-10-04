package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransferEventPublisher;
import com.nodotextil.trazatex.production.application.event.TransferRejectedEvent;
import com.nodotextil.trazatex.production.application.event.TransferStartedEvent;
import com.nodotextil.trazatex.production.application.port.OrganizationAccessPort;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.InvalidTransferException;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.Transfer;
import com.nodotextil.trazatex.production.domain.TransferRepository;
import com.nodotextil.trazatex.production.domain.TransferStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransferService {

    private final TransferRepository transferRepository;
    private final BatchRepository batchRepository;
    private final OrganizationAccessPort organizationAccess;
    private final TransferEventPublisher eventPublisher;
    private final Clock clock;

    public TransferService(
            TransferRepository transferRepository,
            BatchRepository batchRepository,
            OrganizationAccessPort organizationAccess,
            TransferEventPublisher eventPublisher,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.batchRepository = batchRepository;
        this.organizationAccess = organizationAccess;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Transfer start(
            UUID sourceCompanyId,
            UUID destinationCompanyId,
            List<UUID> batchIds) {
        validateCompanies(sourceCompanyId, destinationCompanyId);
        validateBatchIds(batchIds);

        List<Batch> batches = batchIds.stream().map(this::findBatch).toList();
        for (Batch batch : batches) {
            if (!sourceCompanyId.equals(batch.responsibleCompanyId())) {
                throw new InvalidTransferException("Batch belongs to another company");
            }
            if (!batch.isOperationallyEligible()) {
                throw new InvalidTransferException(
                        "All transfer batches must be operationally eligible");
            }
        }

        LocalDateTime startedAt = LocalDateTime.now(clock);
        Transfer transfer = Transfer.start(
                UUID.randomUUID(),
                sourceCompanyId,
                destinationCompanyId,
                batchIds,
                startedAt);
        batches.forEach(Batch::startTransfer);
        batchRepository.saveAll(batches);
        Transfer saved = transferRepository.save(transfer);
        eventPublisher.publish(new TransferStartedEvent(
                saved.id(),
                saved.sourceCompanyId(),
                saved.destinationCompanyId(),
                saved.batchIds(),
                startedAt));
        return saved;
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    @Transactional
    public Transfer accept(UUID id) {
        Transfer transfer = findForUpdate(id);
        List<Batch> batches = loadAndValidatePendingBatches(transfer);
        LocalDateTime acceptedAt = LocalDateTime.now(clock);

        transfer.accept(acceptedAt);
        batches.forEach(batch -> batch.acceptTransfer(transfer.destinationCompanyId()));
        batchRepository.saveAll(batches);
        Transfer saved = transferRepository.save(transfer);
        eventPublisher.publish(new TransferAcceptedEvent(
                saved.id(),
                saved.sourceCompanyId(),
                saved.destinationCompanyId(),
                acceptedAt));
        return saved;
    }

    @Transactional
    public Transfer reject(UUID id, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new InvalidTransferException("Rejection reason is required");
        }
        Transfer transfer = findForUpdate(id);
        List<Batch> batches = loadAndValidatePendingBatches(transfer);
        LocalDateTime rejectedAt = LocalDateTime.now(clock);

        transfer.reject(rejectionReason, rejectedAt);
        batches.forEach(Batch::rejectTransfer);
        batchRepository.saveAll(batches);
        Transfer saved = transferRepository.save(transfer);
        eventPublisher.publish(new TransferRejectedEvent(
                saved.id(),
                saved.sourceCompanyId(),
                saved.destinationCompanyId(),
                saved.rejectionReason(),
                rejectedAt));
        return saved;
    }

    private void validateCompanies(UUID sourceCompanyId, UUID destinationCompanyId) {
        if (sourceCompanyId == null || destinationCompanyId == null) {
            throw new InvalidTransferException("Source and destination companies are required");
        }
        if (sourceCompanyId.equals(destinationCompanyId)) {
            throw new InvalidTransferException(
                    "Destination company must differ from source company");
        }
        if (!organizationAccess.isCompanyActive(sourceCompanyId)
                || !organizationAccess.isCompanyActive(destinationCompanyId)) {
            throw new InvalidTransferException("Both companies must be active");
        }
        if (!organizationAccess.shareSameLicense(sourceCompanyId, destinationCompanyId)) {
            throw new InvalidTransferException(
                    "Source and destination companies must share the same license");
        }
    }

    private static void validateBatchIds(List<UUID> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            throw new InvalidTransferException("A transfer requires at least one batch");
        }
        if (batchIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(batchIds).size() != batchIds.size()) {
            throw new InvalidTransferException("Transfer batches must be distinct and valid");
        }
    }

    private Batch findBatch(UUID id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
    }

    private Transfer findForUpdate(UUID id) {
        return transferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    private List<Batch> loadAndValidatePendingBatches(Transfer transfer) {
        if (transfer.status() != TransferStatus.PENDING) {
            throw new InvalidTransferException("Transfer is already resolved");
        }
        List<Batch> batches = transfer.batchIds().stream().map(this::findBatch).toList();
        for (Batch batch : batches) {
            if (batch.operationalPhase() != OperationalPhase.IN_TRANSFER
                    || !transfer.sourceCompanyId().equals(batch.responsibleCompanyId())) {
                throw new InvalidTransferException("Transfer batch state changed");
            }
        }
        return batches;
    }
}
