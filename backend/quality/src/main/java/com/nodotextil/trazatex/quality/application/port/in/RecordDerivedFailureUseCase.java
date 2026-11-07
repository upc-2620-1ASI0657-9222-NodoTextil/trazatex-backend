package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;

import java.util.List;
import java.util.UUID;

/** US34 / RF-060: registra los avisos de {@code PossibleDerivedFailureDetected}. La retención la manda el job, no el consumidor. */
public interface RecordDerivedFailureUseCase {

    void record(UUID failureId, UUID sourceBatchId, List<AffectedBatch> affected);
}
