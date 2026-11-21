package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;

import java.util.List;
import java.util.UUID;

public interface RecordDerivedFailureUseCase {

    void record(UUID failureId, UUID sourceBatchId, List<AffectedBatch> affected);
}
