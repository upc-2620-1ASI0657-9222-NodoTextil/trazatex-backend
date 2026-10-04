package com.nodotextil.trazatex.quality.application.port;

import com.nodotextil.trazatex.quality.domain.BatchQuality;

import java.util.Optional;
import java.util.UUID;

public interface BatchQualityRepository {

    Optional<BatchQuality> findByBatchId(UUID batchId);

    BatchQuality save(BatchQuality batchQuality);
}
