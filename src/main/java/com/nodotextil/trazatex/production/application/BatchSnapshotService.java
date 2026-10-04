package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.contract.BatchSnapshot;
import com.nodotextil.trazatex.production.application.contract.BatchSnapshotQuery;
import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchRepository;

import java.util.*;

public class BatchSnapshotService implements BatchSnapshotQuery {

    private final BatchRepository batchRepository;

    public BatchSnapshotService(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Override
    public Optional<BatchSnapshot> findById(UUID batchId) {
        return batchRepository.findById(batchId).map(BatchSnapshotService::toSnapshot);
    }

    @Override
    public List<BatchSnapshot> findAllByIds(Collection<UUID> batchIds) {
        return batchIds.stream()
                .map(batchRepository::findById)
                .flatMap(Optional::stream)
                .map(BatchSnapshotService::toSnapshot)
                .toList();
    }

    @Override
    public Optional<BatchSnapshot> findByQrCode(String qrCode) {
        return batchRepository.findByQrCode(qrCode).map(BatchSnapshotService::toSnapshot);
    }

    private static BatchSnapshot toSnapshot(Batch batch) {
        Map<String, Object> configurable = new LinkedHashMap<>();
        putIfPresent(configurable, "supplierName", batch.supplierName());
        putIfPresent(configurable, "geographicOrigin", batch.geographicOrigin());
        putIfPresent(configurable, "quantityKg", batch.quantityKg());
        putIfPresent(configurable, "composition", batch.composition().stream()
                .map(component -> component.material() + " " + component.percentage() + "%")
                .toList());
        putIfPresent(configurable, "receptionCharacteristics", batch.receptionCharacteristics());

        return new BatchSnapshot(
                batch.id(),
                batch.traceabilityId(),
                batch.qrCode(),
                batch.responsibleCompanyId(),
                batch.materialType().name(),
                batch.operationalPhase().name(),
                batch.finalProduct(),
                batch.registeredAt(),
                configurable);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
