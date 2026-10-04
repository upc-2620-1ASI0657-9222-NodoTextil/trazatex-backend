package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryBatchRepository implements BatchRepository {

    private final Map<UUID, Batch> batches = new HashMap<>();

    @Override
    public Batch save(Batch batch) {
        batches.put(batch.id(), batch);
        return batch;
    }

    @Override
    public List<Batch> saveAll(List<Batch> batchesToSave) {
        batchesToSave.forEach(batch -> batches.put(batch.id(), batch));
        return List.copyOf(batchesToSave);
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return Optional.ofNullable(batches.get(id));
    }

    @Override
    public boolean existsByTraceabilityId(String traceabilityId) {
        return batches.values().stream()
                .anyMatch(batch -> batch.traceabilityId().equals(traceabilityId));
    }

    @Override
    public boolean existsByQrCode(String qrCode) {
        return batches.values().stream()
                .anyMatch(batch -> batch.qrCode().equals(qrCode));
    }

    int size() {
        return batches.size();
    }
}
