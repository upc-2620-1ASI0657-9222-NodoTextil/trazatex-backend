package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.Failure;

import java.util.Optional;
import java.util.UUID;

public interface FailureRepository {

    Optional<Failure> findById(UUID failureId);

    Optional<Failure> findActiveByBatchId(UUID batchId);

    Failure save(Failure failure);
}
