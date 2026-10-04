package com.nodotextil.trazatex.traceability.infrastructure.adapter;

import com.nodotextil.trazatex.production.application.contract.BatchSnapshot;
import com.nodotextil.trazatex.production.application.contract.BatchSnapshotQuery;
import com.nodotextil.trazatex.quality.application.contract.QualityStatusQuery;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProductionBatchInfoAdapter implements BatchInfoPort {

    private final BatchSnapshotQuery batchSnapshotQuery;
    private final QualityStatusQuery qualityStatusQuery;

    ProductionBatchInfoAdapter(BatchSnapshotQuery batchSnapshotQuery, QualityStatusQuery qualityStatusQuery) {
        this.batchSnapshotQuery = batchSnapshotQuery;
        this.qualityStatusQuery = qualityStatusQuery;
    }

    @Override
    public Optional<LotInfo> findById(UUID batchId) {
        return batchSnapshotQuery.findById(batchId).map(this::toLotInfo);
    }

    @Override
    public Map<UUID, LotInfo> findAllByIds(Collection<UUID> batchIds) {
        Map<UUID, QualityStatus> statuses = qualityStatusQuery.findStatuses(batchIds);
        Map<UUID, LotInfo> result = new LinkedHashMap<>();
        batchSnapshotQuery.findAllByIds(batchIds).forEach(snapshot -> result.put(
                snapshot.batchId(), toLotInfo(snapshot, statuses.get(snapshot.batchId()))));
        return result;
    }

    @Override
    public Optional<LotInfo> findByQrCode(String qrCode) {
        return batchSnapshotQuery.findByQrCode(qrCode).map(this::toLotInfo);
    }

    private LotInfo toLotInfo(BatchSnapshot snapshot) {
        QualityStatus status = qualityStatusQuery.findStatus(snapshot.batchId()).orElse(QualityStatus.NOT_REVIEWED);
        return toLotInfo(snapshot, status);
    }

    private static LotInfo toLotInfo(BatchSnapshot snapshot, QualityStatus status) {
        return new LotInfo(
                snapshot.batchId(),
                snapshot.traceabilityId(),
                snapshot.qrCode(),
                snapshot.responsibleCompanyId(),
                snapshot.materialType(),
                snapshot.operationalPhase(),
                (status == null ? QualityStatus.NOT_REVIEWED : status).name(),
                snapshot.finalProduct(),
                snapshot.registeredAt(),
                snapshot.configurableData());
    }
}
