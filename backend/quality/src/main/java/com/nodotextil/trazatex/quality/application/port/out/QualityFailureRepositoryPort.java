package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityFailureRepositoryPort {

    void insert(QualityFailure failure, Instant createdAt);

    Optional<QualityFailure> findById(UUID failureId);

    Optional<QualityFailure> findByControlId(UUID controlId);

    Optional<QualityFailure> findByIdForUpdate(UUID failureId);

    PageResult<QualityFailure> findPageByCompany(UUID companyId, PageRequest request);

    Optional<QualityFailure> findOpenByBatchForUpdate(UUID batchId);

    void save(QualityFailure failure);

    void saveDiscardRequest(QualityFailure failure, Instant nextAttemptAt);

    List<ClaimedDiscard> claimPendingDiscards(Instant now, int limit, Duration lease);

    void recordFailedAttempt(UUID failureId, int attempts, Instant nextAttemptAt, String error);

    void reschedule(UUID failureId, Instant nextAttemptAt);
}
