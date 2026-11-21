package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityControlRepositoryPort {

    void insertStarting(QualityControl control, Instant nextAttemptAt);

    Optional<QualityControl> findById(UUID controlId);

    PageResult<QualityControl> findPageByCompany(UUID companyId, PageRequest request);

    Optional<QualityControl> findByIdForUpdate(UUID controlId);

    void save(QualityControl control);

    List<ClaimedStart> claimPendingStarts(Instant now, int limit, Duration lease);

    void saveFinishRequest(QualityControl control, Instant nextAttemptAt);

    List<ClaimedStart> claimPendingFinishes(Instant now, int limit, Duration lease);

    void recordFailedAttempt(UUID controlId, int attempts, Instant nextAttemptAt, String error);

    void reschedule(UUID controlId, Instant nextAttemptAt);
}
