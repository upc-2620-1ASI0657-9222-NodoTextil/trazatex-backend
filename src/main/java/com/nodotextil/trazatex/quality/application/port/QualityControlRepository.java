package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.QualityControl;

import java.util.Optional;
import java.util.UUID;

public interface QualityControlRepository {

    Optional<QualityControl> findById(UUID controlId);

    boolean existsByBatchId(UUID batchId);

    boolean existsActiveByBatchId(UUID batchId);

    QualityControl save(QualityControl qualityControl);
}
