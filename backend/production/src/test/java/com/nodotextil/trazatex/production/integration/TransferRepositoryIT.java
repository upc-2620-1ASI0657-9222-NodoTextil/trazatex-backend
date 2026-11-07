package com.nodotextil.trazatex.production.integration;

import com.nodotextil.trazatex.production.application.port.out.Page;
import com.nodotextil.trazatex.production.application.port.out.TransferSearchCriteria;
import com.nodotextil.trazatex.production.application.port.out.TransferSearchCriteria.Direction;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.production.domain.model.Transfer;
import com.nodotextil.trazatex.production.domain.model.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static org.assertj.core.api.Assertions.assertThat;

class TransferRepositoryIT extends IntegrationTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private final CompanyProjection target = CompanyProjection.of(OTHER_COMPANY, "Tintorería Sur", CompanyStatus.ACTIVE, START);

    private Transfer initiate(UUID origin, CompanyProjection to, java.time.Instant at, Batch... lots) {
        return inTx(() -> {
            List<Batch> locked = batches.findAllForUpdate(java.util.Arrays.stream(lots).map(Batch::id).toList());
            Transfer transfer = Transfer.initiate(UUID.randomUUID(), origin, to, locked, OPERATOR, at);
            batches.saveAll(locked);
            transfers.save(transfer);
            return transfer;
        });
    }

    @Test
    void anInitiatedTransferRoundTripsWithItsBatchesInOrder() {
        Batch a = savedBatch("10");
        Batch b = savedBatch("20");

        Transfer initiated = initiate(COMPANY, target, START, b, a);

        Transfer loaded = inTx(() -> transfers.findById(initiated.id()).orElseThrow());
        assertThat(loaded.originCompanyId()).isEqualTo(COMPANY);
        assertThat(loaded.targetCompanyId()).isEqualTo(OTHER_COMPANY);
        assertThat(loaded.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(loaded.departureTime()).isEqualTo(START);
        assertThat(loaded.initiatedBy()).isEqualTo(OPERATOR);
        assertThat(loaded.batchIds()).containsExactlyInAnyOrder(a.id(), b.id()).hasSize(2);
        assertThat(loaded.resolvedAt()).isNull();
        assertThat(loaded.resolvedBy()).isNull();
        assertThat(loaded.rejectionReason()).isNull();
        assertThat(inTx(() -> batches.findById(a.id()).orElseThrow()).phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    @Test
    void acceptingPersistsTheResolutionAndMovesTheBatches() {
        Batch a = savedBatch("10");
        Transfer initiated = initiate(COMPANY, target, START, a);
        var received = START.plus(Duration.ofHours(2));

        inTx(() -> {
            Transfer transfer = transfers.findByIdForUpdate(initiated.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(transfer.batchIds());
            transfer.accept(OTHER_COMPANY, locked, ADMIN, received);
            batches.saveAll(locked);
            transfers.save(transfer);
        });

        Transfer loaded = inTx(() -> transfers.findById(initiated.id()).orElseThrow());
        assertThat(loaded.status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(loaded.receptionTime()).isEqualTo(received);
        assertThat(loaded.resolvedBy()).isEqualTo(ADMIN);
        assertThat(loaded.rejectionReason()).isNull();
        Batch moved = inTx(() -> batches.findById(a.id()).orElseThrow());
        assertThat(moved.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(moved.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(moved.traceabilityId()).isEqualTo(a.traceabilityId());
    }

    @Test
    void rejectingPersistsTheReasonAndReturnsTheBatchesToTheOrigin() {
        Batch a = savedBatch("10");
        Transfer initiated = initiate(COMPANY, target, START, a);

        inTx(() -> {
            Transfer transfer = transfers.findByIdForUpdate(initiated.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(transfer.batchIds());
            transfer.reject(OTHER_COMPANY, locked, "Calidad insuficiente", ADMIN, START.plusSeconds(30));
            batches.saveAll(locked);
            transfers.save(transfer);
        });

        Transfer loaded = inTx(() -> transfers.findById(initiated.id()).orElseThrow());
        assertThat(loaded.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(loaded.rejectionReason()).isEqualTo("Calidad insuficiente");
        assertThat(loaded.receptionTime()).isNull();
        assertThat(loaded.resolvedAt()).isEqualTo(START.plusSeconds(30));
        Batch back = inTx(() -> batches.findById(a.id()).orElseThrow());
        assertThat(back.companyId()).isEqualTo(COMPANY);
        assertThat(back.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void aBatchCanBeTransferredAgainAfterARejection() {
        Batch a = savedBatch("10");
        Transfer first = initiate(COMPANY, target, START, a);
        inTx(() -> {
            Transfer transfer = transfers.findByIdForUpdate(first.id()).orElseThrow();
            List<Batch> locked = batches.findAllForUpdate(transfer.batchIds());
            transfer.reject(OTHER_COMPANY, locked, "No", ADMIN, START.plusSeconds(1));
            batches.saveAll(locked);
            transfers.save(transfer);
        });

        Transfer second = initiate(COMPANY, target, START.plusSeconds(10), a);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(count("SELECT count(*) FROM transfer_batches WHERE batch_id = ?", a.id())).isEqualTo(2);
    }

    @Test
    void searchSeparatesOutgoingAndIncomingTransfersNewestFirst() {
        CompanyProjection third = CompanyProjection.of(THIRD, "Acabados Este", CompanyStatus.ACTIVE, START);
        Transfer sentOld = initiate(COMPANY, target, START.minusSeconds(100), savedBatch("1"));
        Transfer sentNew = initiate(COMPANY, third, START, savedBatch("2"));
        Transfer received = inTx(() -> {
            Batch foreign = batchFactory.register(registration("3"), OTHER_COMPANY, OPERATOR, START);
            batches.save(foreign);
            Transfer transfer = Transfer.initiate(UUID.randomUUID(), OTHER_COMPANY,
                    CompanyProjection.of(COMPANY, "Propia", CompanyStatus.ACTIVE, START), List.of(foreign), OPERATOR, START.minusSeconds(50));
            batches.save(foreign);
            transfers.save(transfer);
            return transfer;
        });
        initiate(OTHER_COMPANY, third, START, inTx(() -> {
            Batch other = batchFactory.register(registration("4"), OTHER_COMPANY, OPERATOR, START);
            batches.save(other);
            return other;
        }));

        assertThat(ids(new TransferSearchCriteria(COMPANY, Direction.OUTGOING, null, 0, 20))).containsExactly(sentNew.id(), sentOld.id());
        assertThat(ids(new TransferSearchCriteria(COMPANY, Direction.INCOMING, null, 0, 20))).containsExactly(received.id());
        assertThat(ids(new TransferSearchCriteria(COMPANY, Direction.ALL, null, 0, 20))).containsExactly(sentNew.id(), received.id(), sentOld.id());
        assertThat(ids(new TransferSearchCriteria(COMPANY, Direction.ALL, TransferStatus.ACCEPTED, 0, 20))).isEmpty();
        assertThat(ids(new TransferSearchCriteria(COMPANY, Direction.ALL, TransferStatus.PENDING, 0, 2))).containsExactly(sentNew.id(), received.id());
        Page<Transfer> page = inTx(() -> transfers.search(new TransferSearchCriteria(THIRD, Direction.INCOMING, null, 0, 20)));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(ids(new TransferSearchCriteria(UUID.randomUUID(), Direction.ALL, null, 0, 20))).isEmpty();
    }

    private List<UUID> ids(TransferSearchCriteria criteria) {
        return inTx(() -> transfers.search(criteria)).items().stream().map(Transfer::id).toList();
    }
}
