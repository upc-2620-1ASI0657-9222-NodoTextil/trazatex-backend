package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.OutputBatch;
import com.nodotextil.trazatex.quality.application.dto.SplitChild;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;

import java.util.List;
import java.util.UUID;

public interface SyncBatchProjectionUseCase {

    void registered(UUID batchId, UUID companyId, String traceabilityId);

    default void registered(UUID batchId, UUID companyId) {
        registered(batchId, companyId, null);
    }

    void split(UUID companyId, List<SplitChild> children);

    void transformationOutputs(UUID companyId, List<OutputBatch> outputs);

    void phaseChanged(UUID batchId, UUID companyId, BatchPhase newPhase, boolean qualityHold, long batchVersion);

    void transferAccepted(List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId);
}
