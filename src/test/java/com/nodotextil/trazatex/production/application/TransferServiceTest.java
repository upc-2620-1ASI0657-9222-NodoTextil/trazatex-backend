package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransferEventPublisher;
import com.nodotextil.trazatex.production.application.event.TransferRejectedEvent;
import com.nodotextil.trazatex.production.application.event.TransferStartedEvent;
import com.nodotextil.trazatex.production.application.port.OrganizationAccessPort;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidTransferException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.Transfer;
import com.nodotextil.trazatex.production.domain.TransferRepository;
import com.nodotextil.trazatex.production.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private static final UUID SOURCE =
            UUID.fromString("b1e8481a-0613-4d19-b130-f4f1ef95fb83");
    private static final UUID DESTINATION =
            UUID.fromString("fbe91a87-280f-42a1-9b39-d3dfb9763d3b");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC);

    private InMemoryBatchRepository batchRepository;
    private InMemoryTransferRepository transferRepository;
    private TestTransferEventPublisher eventPublisher;
    private TransferService service;

    @BeforeEach
    void setUp() {
        batchRepository = new InMemoryBatchRepository();
        transferRepository = new InMemoryTransferRepository();
        eventPublisher = new TestTransferEventPublisher();
        service = new TransferService(
                transferRepository,
                batchRepository,
                new StubOrganizationAccessPort(),
                eventPublisher,
                FIXED_CLOCK);
    }

    @Test
    void startsTransferWithEligibleWholeBatches() {
        Batch first = saveBatch("TRZ-1", "QR-1", false);
        Batch second = saveBatch("TRZ-2", "QR-2", false);

        Transfer transfer = service.start(SOURCE, DESTINATION, List.of(first.id(), second.id()));

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.batchIds()).containsExactly(first.id(), second.id());
        assertThat(first.operationalPhase()).isEqualTo(OperationalPhase.IN_TRANSFER);
        assertThat(second.operationalPhase()).isEqualTo(OperationalPhase.IN_TRANSFER);
        assertThat(eventPublisher.started).singleElement().satisfies(event -> {
            assertThat(event.transferId()).isEqualTo(transfer.id());
            assertThat(event.batchIds()).containsExactly(first.id(), second.id());
        });
    }

    @Test
    void acceptsTransferAndPreservesBatchIdentityAndCodes() {
        Batch batch = saveBatch("TRZ-KEEP", "QR-KEEP", false);
        UUID batchId = batch.id();
        String traceabilityId = batch.traceabilityId();
        String qrCode = batch.qrCode();
        Transfer pending = service.start(SOURCE, DESTINATION, List.of(batch.id()));

        Transfer accepted = service.accept(pending.id());
        Batch transferred = batchRepository.findById(batchId).orElseThrow();

        assertThat(accepted.status()).isEqualTo(TransferStatus.RECEIVED);
        assertThat(transferred.id()).isEqualTo(batchId);
        assertThat(transferred.traceabilityId()).isEqualTo(traceabilityId);
        assertThat(transferred.qrCode()).isEqualTo(qrCode);
        assertThat(transferred.responsibleCompanyId()).isEqualTo(DESTINATION);
        assertThat(transferred.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(eventPublisher.accepted).singleElement()
                .extracting(TransferAcceptedEvent::transferId)
                .isEqualTo(pending.id());
    }

    @Test
    void rejectsTransferAndRestoresAvailabilityWithoutChangingOwner() {
        Batch batch = saveBatch("TRZ-REJECT", "QR-REJECT", false);
        Transfer pending = service.start(SOURCE, DESTINATION, List.of(batch.id()));

        Transfer rejected = service.reject(pending.id(), "Destination cannot receive it");

        assertThat(rejected.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Destination cannot receive it");
        assertThat(batch.responsibleCompanyId()).isEqualTo(SOURCE);
        assertThat(batch.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(eventPublisher.rejected).singleElement()
                .extracting(TransferRejectedEvent::rejectionReason)
                .isEqualTo("Destination cannot receive it");
    }

    @Test
    void requiresReasonToReject() {
        Batch batch = saveBatch("TRZ-NO-REASON", "QR-NO-REASON", false);
        Transfer pending = service.start(SOURCE, DESTINATION, List.of(batch.id()));

        assertThatThrownBy(() -> service.reject(pending.id(), " "))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("reason");
        assertThat(pending.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(batch.operationalPhase()).isEqualTo(OperationalPhase.IN_TRANSFER);
    }

    @Test
    void rejectsFinalProductAtTransferStart() {
        Batch finalProduct = saveBatch("TRZ-FINAL", "QR-FINAL", true);

        assertThatThrownBy(() -> service.start(
                SOURCE, DESTINATION, List.of(finalProduct.id())))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("operationally eligible");
        assertThat(finalProduct.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(transferRepository.transfers).isEmpty();
    }

    private Batch saveBatch(String traceabilityId, String qrCode, boolean finalProduct) {
        Batch batch = Batch.register(
                UUID.randomUUID(),
                traceabilityId,
                qrCode,
                SOURCE,
                "Supplier",
                "Arequipa, Peru",
                MaterialType.FIBER,
                new BigDecimal("25"),
                List.of(new CompositionComponent("Cotton", new BigDecimal("100"))),
                LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC),
                "Clean",
                finalProduct,
                null,
                null,
                null,
                null,
                null);
        return batchRepository.save(batch);
    }

    private static final class StubOrganizationAccessPort implements OrganizationAccessPort {

        @Override
        public boolean isCompanyActive(UUID companyId) {
            return SOURCE.equals(companyId) || DESTINATION.equals(companyId);
        }

        @Override
        public boolean shareSameLicense(UUID sourceCompanyId, UUID destinationCompanyId) {
            return SOURCE.equals(sourceCompanyId) && DESTINATION.equals(destinationCompanyId);
        }
    }

    private static final class InMemoryTransferRepository implements TransferRepository {

        private final Map<UUID, Transfer> transfers = new HashMap<>();

        @Override
        public Transfer save(Transfer transfer) {
            transfers.put(transfer.id(), transfer);
            return transfer;
        }

        @Override
        public Optional<Transfer> findById(UUID id) {
            return Optional.ofNullable(transfers.get(id));
        }
    }

    private static final class TestTransferEventPublisher implements TransferEventPublisher {

        private final List<TransferStartedEvent> started = new ArrayList<>();
        private final List<TransferAcceptedEvent> accepted = new ArrayList<>();
        private final List<TransferRejectedEvent> rejected = new ArrayList<>();

        @Override
        public void publish(TransferStartedEvent event) {
            started.add(event);
        }

        @Override
        public void publish(TransferAcceptedEvent event) {
            accepted.add(event);
        }

        @Override
        public void publish(TransferRejectedEvent event) {
            rejected.add(event);
        }
    }
}
