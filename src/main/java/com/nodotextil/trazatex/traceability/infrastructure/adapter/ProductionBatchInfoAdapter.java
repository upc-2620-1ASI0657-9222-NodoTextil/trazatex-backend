package com.nodotextil.trazatex.traceability.infrastructure.adapter;

import com.nodotextil.trazatex.production.application.contract.BatchSnapshot;
import com.nodotextil.trazatex.production.application.contract.BatchSnapshotQuery;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
class ProductionBatchInfoAdapter implements BatchInfoPort {

    private final BatchSnapshotQuery batchSnapshotQuery;

    ProductionBatchInfoAdapter(BatchSnapshotQuery batchSnapshotQuery) {
        this.batchSnapshotQuery = batchSnapshotQuery;
    }

    @Override
    public Optional<LotInfo> findById(UUID batchId) {
        return batchSnapshotQuery.findById(batchId).map(ProductionBatchInfoAdapter::toLotInfo);
    }

    @Override
    public Map<UUID, LotInfo> findAllByIds(Collection<UUID> batchIds) {
        Map<UUID, LotInfo> result = new LinkedHashMap<>();
        batchSnapshotQuery.findAllByIds(batchIds)
                .forEach(snapshot -> result.put(snapshot.batchId(), toLotInfo(snapshot)));
        return result;
    }

    @Override
    public Optional<LotInfo> findByQrCode(String qrCode) {
        return batchSnapshotQuery.findByQrCode(qrCode).map(ProductionBatchInfoAdapter::toLotInfo);
    }

    private static LotInfo toLotInfo(BatchSnapshot snapshot) {
        return new LotInfo(
                snapshot.batchId(),
                snapshot.traceabilityId(),
                snapshot.qrCode(),
                snapshot.responsibleCompanyId(),
                snapshot.materialType(),
                snapshot.operationalPhase(),
                snapshot.finalProduct(),
                snapshot.registeredAt(),
                snapshot.configurableData());
    }
}
