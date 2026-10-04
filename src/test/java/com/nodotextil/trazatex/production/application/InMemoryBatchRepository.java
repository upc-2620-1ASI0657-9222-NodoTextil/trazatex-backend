package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import java.util.Comparator;
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
    public List<Batch> search(BatchSearchCriteria criteria) {
        return batches.values().stream()
                .filter(batch -> criteria.traceabilityId() == null
                        || batch.traceabilityId().equals(criteria.traceabilityId()))
                .filter(batch -> criteria.materialType() == null
                        || batch.materialType() == criteria.materialType())
                .filter(batch -> criteria.operationalPhase() == null
                        || batch.operationalPhase() == criteria.operationalPhase())
                .filter(batch -> criteria.registeredFrom() == null
                        || !batch.registeredAt().isBefore(criteria.registeredFrom()))
                .filter(batch -> criteria.registeredTo() == null
                        || !batch.registeredAt().isAfter(criteria.registeredTo()))
                .sorted(Comparator.comparing(Batch::registeredAt).thenComparing(Batch::id))
                .toList();
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
