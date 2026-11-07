package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.Evidence;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de evidencias y de la cola de bytes pendientes (PostgreSQL). */
public interface EvidenceRepositoryPort {

    /** Una evidencia nueva; {@code queuedContent} es nulo si ya está subida. Todas en la misma transacción. */
    record NewEvidence(Evidence evidence, byte[] queuedContent) {
    }

    /** Una evidencia reclamada por el job, con sus bytes. */
    record PendingUpload(Evidence evidence, byte[] content) {
    }

    void saveAll(List<NewEvidence> evidences);

    /** Las evidencias de ese dueño que subió esa empresa, de la más antigua a la más nueva. */
    List<Evidence> findByOwnerAndCompany(EvidenceOwnerType ownerType, UUID ownerId, UUID companyId);

    /**
     * Reclama la siguiente evidencia {@code PENDING} vencida ({@code FOR UPDATE SKIP LOCKED}) y le pone un <i>lease</i>
     * ({@code next_attempt_at = leaseUntil}) para que otro job no la tome mientras se sube. Dentro de una transacción corta:
     * la subida HTTP se hace fuera de ella.
     */
    Optional<PendingUpload> claimNextDue(Instant now, Instant leaseUntil);

    /** Guarda el resultado de un intento; si quedó {@code UPLOADED} borra sus bytes de la cola (en {@code FAILED} se conservan). */
    void update(Evidence evidence);
}
