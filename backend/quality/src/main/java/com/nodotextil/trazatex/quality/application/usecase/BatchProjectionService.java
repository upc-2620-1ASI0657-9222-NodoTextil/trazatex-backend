package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.OutputBatch;
import com.nodotextil.trazatex.quality.application.dto.SplitChild;
import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplica los eventos de Production a la proyección. Los tópicos no tienen orden entre sí: un lote puede conocerse primero por su
 * {@code BatchPhaseChanged} (fila parcial) y completarse después con el evento que lo da a conocer, sin pisar una versión mayor.
 */
@Service
@Transactional
class BatchProjectionService implements SyncBatchProjectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(BatchProjectionService.class);

    private final BatchProjectionRepositoryPort batches;
    private final ImpactedBatchRepositoryPort impacted;
    private final Clock clock;

    BatchProjectionService(BatchProjectionRepositoryPort batches, ImpactedBatchRepositoryPort impacted, Clock clock) {
        this.batches = batches;
        this.impacted = impacted;
        this.clock = clock;
    }

    @Override
    public void registered(UUID batchId, UUID companyId, String traceabilityId) {
        Instant now = clock.instant();
        born(batchId, () -> BatchProjection.registered(batchId, companyId, traceabilityId, now), QualityStatus.NOT_REVIEWED, traceabilityId, now);
    }

    @Override
    public void split(UUID companyId, List<SplitChild> children) {
        Instant now = clock.instant();
        for (SplitChild child : children) {
            born(child.batchId(), () -> BatchProjection.splitChild(child.batchId(), companyId, child.qualityStatus(), child.traceabilityId(), now),
                    child.qualityStatus(), child.traceabilityId(), now);
        }
    }

    @Override
    public void transformationOutputs(UUID companyId, List<OutputBatch> outputs) {
        Instant now = clock.instant();
        for (OutputBatch output : outputs) {
            born(output.batchId(), () -> BatchProjection.registered(output.batchId(), companyId, output.traceabilityId(), now), QualityStatus.NOT_REVIEWED,
                    output.traceabilityId(), now);
        }
    }

    @Override
    public void phaseChanged(UUID batchId, UUID companyId, BatchPhase newPhase, boolean qualityHold, long batchVersion) {
        Instant now = clock.instant();
        Optional<BatchProjection> existing = batches.findByIdForUpdate(batchId);
        if (existing.isEmpty()) {
            batches.insert(BatchProjection.partial(batchId, companyId, newPhase, qualityHold, batchVersion, now));
            impacted.markDue(batchId, now);
            return;
        }
        BatchProjection batch = existing.get();
        if (batch.applyPhaseChanged(companyId, newPhase, qualityHold, batchVersion, now)) {
            batches.update(batch);
            // Novedades del lote: sus avisos de falla derivada pendientes se vuelven a decidir ya (p. ej. volvió a AVAILABLE tras una transferencia)
            impacted.markDue(batchId, now);
        } else {
            log.debug("BatchPhaseChanged del lote {} con versión {} no es más nueva que la {} guardada: se ignora", batchId, batchVersion,
                    batch.batchVersion());
        }
    }

    @Override
    public void transferAccepted(List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId) {
        Instant now = clock.instant();
        for (UUID batchId : batchIds) {
            Optional<BatchProjection> existing = batches.findByIdForUpdate(batchId);
            if (existing.isEmpty()) {
                // El BatchPhaseChanged de la aceptación trae la empresa nueva y creará la fila
                log.debug("TransferAccepted de un lote {} que la proyección no conoce: lo creará su BatchPhaseChanged", batchId);
                continue;
            }
            BatchProjection batch = existing.get();
            switch (batch.applyTransferAccepted(fromCompanyId, toCompanyId, now)) {
                case MOVED -> batches.update(batch);
                case ALREADY_THERE -> log.debug("El lote {} ya estaba con la empresa {}", batchId, toCompanyId);
                case OWNER_MISMATCH -> log.warn("TransferAccepted del lote {} de {} a {}, pero la proyección lo tiene con {}: se ignora",
                        batchId, fromCompanyId, toCompanyId, batch.companyId());
            }
        }
    }

    private void born(UUID batchId, java.util.function.Supplier<BatchProjection> creation, QualityStatus status, String traceabilityId, Instant now) {
        Optional<BatchProjection> existing = batches.findByIdForUpdate(batchId);
        if (existing.isEmpty()) {
            batches.insert(creation.get());
            impacted.markDue(batchId, now);
            return;
        }
        BatchProjection batch = existing.get();
        if (batch.completeFromCreation(status, traceabilityId, now)) {
            batches.update(batch);
            impacted.markDue(batchId, now);
        }
    }
}
