package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;
import com.nodotextil.trazatex.quality.application.port.in.RecordDerivedFailureUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.BatchProjectionRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.ImpactedBatchRepositoryPort;
import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.quality.domain.model.DerivedFailureDecision;
import com.nodotextil.trazatex.quality.domain.model.DerivedFailurePolicy;
import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;
import com.nodotextil.trazatex.quality.domain.model.LockStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US34 / RF-060 (decisión del usuario, 2026-10-23): el aviso <b>siempre</b> se registra en {@code impacted_batch}, con el {@code lockStatus} que
 * decide {@link DerivedFailurePolicy} desde la proyección. Aquí <b>no se llama a Production</b> (corre dentro de la transacción del consumo
 * idempotente de Kafka): los avisos que piden {@code HOLD} quedan {@code PENDING} y vencidos, y el job de retenciones los manda en segundos
 * ({@link DerivedHoldResolver}). La calidad solo cambia cuando el {@code HOLD} se confirma, porque {@code POTENTIAL_DERIVED_FAILURE} implica
 * {@code qualityHold = true}.
 */
@Service
@Transactional
class DerivedFailureService implements RecordDerivedFailureUseCase {

    private static final Logger log = LoggerFactory.getLogger(DerivedFailureService.class);

    static final String DERIVED_FAILURE_RECORDED = "DERIVED_FAILURE_RECORDED";

    private final BatchProjectionRepositoryPort projections;
    private final ImpactedBatchRepositoryPort impacted;
    private final AuditLogPort audit;
    private final PendingCommandSettings settings;
    private final Clock clock;

    DerivedFailureService(BatchProjectionRepositoryPort projections, ImpactedBatchRepositoryPort impacted, AuditLogPort audit, PendingCommandSettings settings,
                          Clock clock) {
        this.projections = projections;
        this.impacted = impacted;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void record(UUID failureId, UUID sourceBatchId, List<AffectedBatch> affected) {
        Instant now = clock.instant();
        // En orden de lote: dos avisos simultáneos que comparten lotes bloquean las filas en el mismo orden y no se interbloquean
        for (AffectedBatch one : affected.stream().sorted(Comparator.comparing(AffectedBatch::batchId)).toList()) {
            BatchProjection batch = projections.findByIdForUpdate(one.batchId()).orElse(null);
            DerivedFailureDecision decision = DerivedFailurePolicy.decide(batch);
            ImpactedBatch notice = ImpactedBatch.detect(failureId, sourceBatchId, one.batchId(), one.commonAncestorBatchId(), decision);
            Instant next = notice.lockStatus() != LockStatus.PENDING ? null : decision.sendsHold() ? now : now.plus(settings.holdWaitPoll());
            if (!impacted.insertIfAbsent(notice, now, next)) {
                log.debug("El aviso de la falla {} sobre el lote {} ya estaba registrado", failureId, one.batchId());
                continue;
            }
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("failureId", failureId.toString());
            changes.put("impactType", notice.impactType().name());
            changes.put("lockStatus", notice.lockStatus().name());
            changes.put("decision", decision.name());
            audit.record(null, batch == null ? one.companyId() : batch.companyId(), DERIVED_FAILURE_RECORDED, "ImpactedBatch", one.batchId().toString(), changes);
            if (decision.warns()) {
                log.warn("Posible falla derivada {} sobre el lote {}: {} (fase {}); aviso {}. Se vuelve a decidir cuando el lote cambie", failureId,
                        one.batchId(), decision, batch == null ? "desconocida" : batch.phase(), notice.lockStatus());
            } else {
                log.info("Posible falla derivada {} sobre el lote {}: {} ({})", failureId, one.batchId(), decision, notice.impactType());
            }
        }
    }
}
