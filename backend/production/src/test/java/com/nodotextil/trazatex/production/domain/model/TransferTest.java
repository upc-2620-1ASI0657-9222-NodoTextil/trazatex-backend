package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.OTHER_COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.availableBatch;
import static com.nodotextil.trazatex.production.domain.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US21 y US22. */
class TransferTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    private final CompanyProjection target = CompanyProjection.of(OTHER_COMPANY, "Tintorería Sur", CompanyStatus.ACTIVE, NOW);

    private Transfer initiate(Batch... batches) {
        return Transfer.initiate(UUID.randomUUID(), COMPANY, target, List.of(batches), OPERATOR, NOW);
    }

    private static void assertCode(Throwable thrown, String code) {
        assertThat(thrown).isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    // --- iniciar (US21) -----------------------------------------------------------------------------------------

    @Test
    void initiatingRegistersTheDepartureAndMovesTheBatchesToInTransfer() {
        Batch a = availableBatch("10");
        Batch b = availableBatch("20");

        Transfer transfer = initiate(a, b);

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.departureTime()).isEqualTo(NOW);
        assertThat(transfer.originCompanyId()).isEqualTo(COMPANY);
        assertThat(transfer.targetCompanyId()).isEqualTo(OTHER_COMPANY);
        assertThat(transfer.initiatedBy()).isEqualTo(OPERATOR);
        assertThat(transfer.batchIds()).containsExactly(a.id(), b.id());
        assertThat(transfer.resolvedAt()).isNull();
        assertThat(transfer.receptionTime()).isNull();
        assertThat(a.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
        assertThat(b.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    // --- solo lotes completos (US21 esc. 2) ----------------------------------------------------------------------

    private Transfer initiateAskingFor(java.util.Map<UUID, Quantity> requested, Batch... batches) {
        return Transfer.initiate(UUID.randomUUID(), COMPANY, target, List.of(batches), requested, OPERATOR, NOW);
    }

    @Test
    void askingForTheWholeQuantityOfEveryBatchIsATransferOfWholeBatches() {
        Batch a = availableBatch("12.5");
        Batch b = availableBatch("20");

        Transfer transfer = initiateAskingFor(java.util.Map.of(a.id(), Quantity.ofKg("12.500"), b.id(), Quantity.ofKg("20")), a, b);

        assertThat(transfer.batchIds()).containsExactly(a.id(), b.id());
        assertThat(a.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    @Test
    void askingForPartOfABatchIsRejectedBeforeAnythingMovesAndAsksToSplitIt() {
        Batch whole = availableBatch("10");
        Batch partial = availableBatch("20");

        for (String kg : new String[]{"5", "19.999", "20.001", "0.001", "40"}) {
            assertThatThrownBy(() -> initiateAskingFor(java.util.Map.of(whole.id(), Quantity.ofKg("10"), partial.id(), Quantity.ofKg(kg)), whole, partial))
                    .satisfies(e -> assertCode(e, ErrorCodes.PARTIAL_TRANSFER_NOT_ALLOWED))
                    .hasMessage("Solo se transfieren lotes completos: divida el lote antes");
        }
        assertThat(whole.phase()).as("el lote completo tampoco se movió").isEqualTo(BatchPhase.AVAILABLE);
        assertThat(partial.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void thePartialCheckComesBeforeTheEnabledCheck() {
        Batch held = batch(BatchPhase.AVAILABLE, true, false, "10");

        assertThatThrownBy(() -> initiateAskingFor(java.util.Map.of(held.id(), Quantity.ofKg("4")), held))
                .satisfies(e -> assertCode(e, ErrorCodes.PARTIAL_TRANSFER_NOT_ALLOWED));
    }

    @Test
    void quantitiesOfBatchesNotInTheSelectionAreIgnoredByTheDomain() {
        Batch a = availableBatch("10");

        Transfer transfer = initiateAskingFor(java.util.Map.of(UUID.randomUUID(), Quantity.ofKg("1")), a);

        assertThat(transfer.batchIds()).containsExactly(a.id());
    }

    @Test
    void aBatchThatIsNotEnabledBlocksTheWholeTransferAndNothingMoves() {
        Batch ok = availableBatch("10");

        assertThatThrownBy(() -> initiate(ok, batch(BatchPhase.AVAILABLE, true, false, "5"))).satisfies(e -> assertCode(e, ErrorCodes.BATCH_ON_QUALITY_HOLD));
        assertThatThrownBy(() -> initiate(ok, batch(BatchPhase.AVAILABLE, false, true, "5"))).satisfies(e -> assertCode(e, ErrorCodes.BATCH_IS_FINAL_PRODUCT));
        assertThatThrownBy(() -> initiate(ok, batch(BatchPhase.IN_TRANSFORMATION, false, false, "5"))).satisfies(e -> assertCode(e, ErrorCodes.INVALID_PHASE_TRANSITION));

        assertThat(ok.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void theTargetMustBeAnotherActiveCompany() {
        Batch batch = availableBatch("10");
        CompanyProjection inactive = CompanyProjection.of(OTHER_COMPANY, "Inactiva", CompanyStatus.INACTIVE, NOW);
        CompanyProjection same = CompanyProjection.of(COMPANY, "Propia", CompanyStatus.ACTIVE, NOW);

        assertThatThrownBy(() -> Transfer.initiate(UUID.randomUUID(), COMPANY, inactive, List.of(batch), OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.TARGET_COMPANY_INACTIVE));
        assertThatThrownBy(() -> Transfer.initiate(UUID.randomUUID(), COMPANY, same, List.of(batch), OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.SAME_COMPANY_TRANSFER));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void needsBatchesOfTheOriginCompanyWithoutRepetitions() {
        Batch batch = availableBatch("10");
        assertThatThrownBy(() -> Transfer.initiate(UUID.randomUUID(), COMPANY, target, List.of(), OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.NO_BATCHES_SELECTED));
        assertThatThrownBy(() -> initiate(batch, batch)).satisfies(e -> assertCode(e, ErrorCodes.DUPLICATE_BATCH));
        assertThatThrownBy(() -> Transfer.initiate(UUID.randomUUID(), OTHER_COMPANY,
                CompanyProjection.of(UUID.randomUUID(), "Otra", CompanyStatus.ACTIVE, NOW), List.of(batch), OPERATOR, NOW))
                .satisfies(e -> assertCode(e, ErrorCodes.BATCH_COMPANY_MISMATCH));
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    // --- aceptar (US22 esc. 1) ----------------------------------------------------------------------------------

    @Test
    void acceptingMovesTheResponsibilityKeepsTheCodesAndRegistersTheReception() {
        Batch batch = availableBatch("10");
        TraceabilityId traceabilityId = batch.traceabilityId();
        QrCode qr = batch.qrCode();
        Transfer transfer = initiate(batch);
        var received = NOW.plus(Duration.ofHours(3));

        transfer.accept(OTHER_COMPANY, List.of(batch), ADMIN, received);

        assertThat(transfer.status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(transfer.receptionTime()).isEqualTo(received);
        assertThat(transfer.resolvedAt()).isEqualTo(received);
        assertThat(transfer.resolvedBy()).isEqualTo(ADMIN);
        assertThat(batch.companyId()).isEqualTo(OTHER_COMPANY);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
        assertThat(batch.traceabilityId()).isEqualTo(traceabilityId);
        assertThat(batch.qrCode()).isEqualTo(qr);
    }

    // --- rechazar (US22 esc. 2) ---------------------------------------------------------------------------------

    @Test
    void rejectingReturnsTheBatchesAvailableToTheOriginCompany() {
        Batch batch = availableBatch("10");
        Transfer transfer = initiate(batch);
        var rejected = NOW.plus(Duration.ofHours(1));

        transfer.reject(OTHER_COMPANY, List.of(batch), "  Calidad insuficiente ", ADMIN, rejected);

        assertThat(transfer.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(transfer.rejectionReason()).isEqualTo("Calidad insuficiente");
        assertThat(transfer.resolvedAt()).as("registra fecha y hora").isEqualTo(rejected);
        assertThat(transfer.receptionTime()).as("no hubo recepción").isNull();
        assertThat(batch.companyId()).isEqualTo(COMPANY);
        assertThat(batch.phase()).isEqualTo(BatchPhase.AVAILABLE);
    }

    @Test
    void rejectingRequiresAReasonAndChangesNothingWithout() {
        Batch batch = availableBatch("10");
        Transfer transfer = initiate(batch);

        for (String reason : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> transfer.reject(OTHER_COMPANY, List.of(batch), reason, ADMIN, NOW))
                    .satisfies(e -> assertCode(e, ErrorCodes.REJECTION_REASON_REQUIRED));
        }
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    // --- quién y cuándo -----------------------------------------------------------------------------------------

    @Test
    void onlyTheTargetCompanyCanResolve() {
        Batch batch = availableBatch("10");
        Transfer transfer = initiate(batch);

        assertThatThrownBy(() -> transfer.accept(COMPANY, List.of(batch), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.NOT_TRANSFER_RECIPIENT));
        assertThatThrownBy(() -> transfer.reject(COMPANY, List.of(batch), "x", ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.NOT_TRANSFER_RECIPIENT));
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFER);
    }

    @Test
    void aResolvedTransferCannotBeResolvedAgain() {
        Batch batch = availableBatch("10");
        Transfer accepted = initiate(batch);
        accepted.accept(OTHER_COMPANY, List.of(batch), ADMIN, NOW);

        assertThatThrownBy(() -> accepted.accept(OTHER_COMPANY, List.of(batch), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.TRANSFER_NOT_PENDING));
        assertThatThrownBy(() -> accepted.reject(OTHER_COMPANY, List.of(batch), "tarde", ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.TRANSFER_NOT_PENDING));

        Batch other = availableBatch("10");
        Transfer rejected = initiate(other);
        rejected.reject(OTHER_COMPANY, List.of(other), "no", ADMIN, NOW);
        assertThatThrownBy(() -> rejected.accept(OTHER_COMPANY, List.of(other), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.TRANSFER_NOT_PENDING));
        assertThat(other.companyId()).isEqualTo(COMPANY);
    }

    @Test
    void theBatchesGivenMustBeTheOnesOfTheTransfer() {
        Batch batch = availableBatch("10");
        Batch extra = batch(BatchPhase.IN_TRANSFER, false, false, "10");
        Transfer transfer = initiate(batch);

        assertThatThrownBy(() -> transfer.accept(OTHER_COMPANY, List.of(extra), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
        assertThatThrownBy(() -> transfer.accept(OTHER_COMPANY, List.of(batch, extra), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
        assertThatThrownBy(() -> transfer.accept(OTHER_COMPANY, List.of(), ADMIN, NOW)).satisfies(e -> assertCode(e, ErrorCodes.BATCHES_DO_NOT_MATCH));
        assertThat(batch.companyId()).isEqualTo(COMPANY);
    }

    @Test
    void restoreRebuildsAnyState() {
        UUID id = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Transfer restored = Transfer.restore(id, COMPANY, OTHER_COMPANY, TransferStatus.REJECTED, List.of(batchId), NOW,
                NOW.plusSeconds(5), "motivo", OPERATOR, ADMIN);

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(restored.rejectionReason()).isEqualTo("motivo");
        assertThat(restored.batchIds()).containsExactly(batchId);
    }

    // --- proyección de empresa (RF-003) -------------------------------------------------------------------------

    @Test
    void anInactiveCompanyIsRejectedByTheProjectionGuard() {
        CompanyProjection company = CompanyProjection.of(COMPANY, "Hilandería Norte", CompanyStatus.ACTIVE, NOW);
        company.ensureActive();

        company.changeStatus(CompanyStatus.INACTIVE, NOW.plusSeconds(1));
        assertThat(company.isActive()).isFalse();
        assertThatThrownBy(company::ensureActive).satisfies(e -> assertCode(e, ErrorCodes.COMPANY_INACTIVE));

        company.changeStatus(CompanyStatus.ACTIVE, NOW.plusSeconds(2));
        company.ensureActive();
        assertThat(company.updatedAt()).isEqualTo(NOW.plusSeconds(2));
    }
}
