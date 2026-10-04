package com.nodotextil.trazatex.production.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {

    Batch save(Batch batch);

    List<Batch> saveAll(List<Batch> batches);

    Optional<Batch> findById(UUID id);

    Optional<Batch> findByQrCode(String qrCode);

    List<Batch> search(BatchSearchCriteria criteria);

    boolean existsByTraceabilityId(String traceabilityId);

    boolean existsByQrCode(String qrCode);
}
