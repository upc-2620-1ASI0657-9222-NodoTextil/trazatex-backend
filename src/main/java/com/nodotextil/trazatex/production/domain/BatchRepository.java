package com.nodotextil.trazatex.production.domain;

import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {

    Batch save(Batch batch);

    Optional<Batch> findById(UUID id);

    boolean existsByTraceabilityId(String traceabilityId);

    boolean existsByQrCode(String qrCode);
}
