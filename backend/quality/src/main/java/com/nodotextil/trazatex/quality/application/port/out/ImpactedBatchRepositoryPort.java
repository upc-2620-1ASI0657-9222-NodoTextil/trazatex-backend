package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.ImpactedBatch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpactedBatchRepositoryPort {

    boolean insertIfAbsent(ImpactedBatch impacted, Instant createdAt, Instant nextAttemptAt);

    Optional<ImpactedBatch> findForUpdate(UUID failureId, UUID batchId);

    void save(ImpactedBatch impacted);

    int markDue(UUID batchId, Instant now);

    List<ClaimedHold> claimPending(Instant now, int limit, Duration lease);

    void recordFailedAttempt(UUID failureId, UUID batchId, int attempts, Instant nextAttemptAt, String error);

    void reschedule(UUID failureId, UUID batchId, Instant nextAttemptAt);

    List<ImpactedBatch> findByFailure(UUID failureId);
}
