package com.nodotextil.trazatex.quality.application.contract;

import java.util.UUID;

public interface ProductionQualityPort {

    void startQualityControl(UUID batchId);

    void finishQualityControl(UUID batchId);
}
