package com.nodotextil.trazatex.production.application.usecase;
import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.port.in.DeleteEvidenceUseCase;
import com.nodotextil.trazatex.production.application.port.out.*;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.util.UUID;
@Service
class EvidenceDeletionService implements DeleteEvidenceUseCase {
    private final BatchRepositoryPort batches;
    private final TransformationRepositoryPort transformations;
    private final EvidenceDeletionPort deletions;
    private final EvidenceStoragePort storage;
    private final AuditLogPort audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    EvidenceDeletionService(BatchRepositoryPort batches, TransformationRepositoryPort transformations, EvidenceDeletionPort deletions,
                            EvidenceStoragePort storage, AuditLogPort audit, Clock clock, PlatformTransactionManager manager) {
        this.batches=batches; this.transformations=transformations; this.deletions=deletions; this.storage=storage;
        this.audit=audit; this.clock=clock; this.transaction=new TransactionTemplate(manager);
    }
    @Override
    public void delete(EvidenceOwnerType type, UUID ownerId, UUID evidenceId, Actor actor) {
        transaction.executeWithoutResult(status -> {
            boolean owned = type == EvidenceOwnerType.BATCH
                    ? batches.findById(ownerId).filter(batch -> batch.companyId().equals(actor.companyId())).isPresent()
                    : transformations.findById(ownerId).filter(process -> process.companyId().equals(actor.companyId())).isPresent();
            if (!owned || !deletions.markDeleted(type, ownerId, evidenceId, actor.companyId(), clock.instant()))
                throw new NotFoundException("EVIDENCE_NOT_FOUND", "La evidencia no existe o no pertenece a tu empresa.");
            audit.record(actor.userId(), actor.companyId(), "EVIDENCE_DELETED", "Evidence", evidenceId.toString(), java.util.Map.of("ownerId", ownerId.toString()));
        });
    }
    /**
     * M5: <b>sin red dentro de una transacción</b>. Tres pasos: elegir la siguiente (transacción corta), borrar la imagen en Cloudinary
     * (sin transacción: puede tardar hasta el timeout y pasa por el circuit breaker) y quitar las referencias (otra transacción corta).
     * Si la red falla para una evidencia, esa fila queda marcada y se aplaza ({@code deferPurge}: 10 s, 20 s ... hasta 5 min) y <b>se sigue con las
     * demás</b>; el job la repite cuando vence la espera. Borrar es idempotente ({@code not found} cuenta como hecho), así que si dos
     * instancias eligen la misma a la vez, ambas borran y la segunda no encuentra nada que purgar: no hay daño, solo una llamada de más.
     */
    private static final Logger log=LoggerFactory.getLogger(EvidenceDeletionService.class);
    @Override
    public int purgeDeleted() {
        int count=0;
        for (int i=0; i<5; i++) {
            java.util.Optional<UUID> next=transaction.execute(status -> deletions.nextDeleted(clock.instant()));
            if (next==null || next.isEmpty()) break;
            UUID id=next.get();
            try {
                storage.delete(id.toString());
            } catch (RuntimeException error) {
                // una evidencia que falla no frena a las demás: se aplaza con espera creciente (en su propia transacción corta) y se sigue
                log.warn("No se pudo borrar la imagen de una evidencia; se reintenta más tarde ({})", error.getClass().getSimpleName());
                transaction.executeWithoutResult(status -> deletions.deferPurge(id, clock.instant()));
                continue;
            }
            transaction.executeWithoutResult(status -> deletions.purge(id));
            count++;
        }
        return count;
    }
}
