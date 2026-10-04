package com.nodotextil.trazatex.quality.application.contract;

import java.util.UUID;

public interface ProductionQualityPort {

    void startQualityControl(UUID batchId);

    void finishQualityControl(UUID batchId);

    void blockBatch(UUID batchId);

    void unblockBatch(UUID batchId);

    void discardBatch(UUID batchId);
}
