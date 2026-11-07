package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpactedBatchRepositoryPort {

    /**
     * Registra el aviso si no existía ({@code (failureId, batchId)} es la clave: el mismo aviso dos veces no duplica). {@code nextAttemptAt}
     * solo se guarda si queda {@code PENDING}.
     *
     * @return si se insertó
     */
    boolean insertIfAbsent(ImpactedBatch impacted, Instant createdAt, Instant nextAttemptAt);

    Optional<ImpactedBatch> findForUpdate(UUID failureId, UUID batchId);

    /** Guarda el {@code lockStatus}; fuera de {@code PENDING} apaga el reintento. */
    void save(ImpactedBatch impacted);

    /** Hay novedades del lote (un {@code BatchPhaseChanged}): sus avisos {@code PENDING} se vuelven a decidir ya. */
    int markDue(UUID batchId, Instant now);

    /** Reclama con {@code FOR UPDATE SKIP LOCKED} los avisos {@code PENDING} vencidos y los arrienda. */
    List<ClaimedHold> claimPending(Instant now, int limit, Duration lease);

    void recordFailedAttempt(UUID failureId, UUID batchId, int attempts, Instant nextAttemptAt, String error);

    void reschedule(UUID failureId, UUID batchId, Instant nextAttemptAt);

    /** Para consultas y pruebas. */
    List<ImpactedBatch> findByFailure(UUID failureId);
}
