package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.QualityControl;

import java.util.UUID;

public interface QualityControlRepository {

    boolean existsActiveByBatchId(UUID batchId);

    QualityControl save(QualityControl qualityControl);
}
