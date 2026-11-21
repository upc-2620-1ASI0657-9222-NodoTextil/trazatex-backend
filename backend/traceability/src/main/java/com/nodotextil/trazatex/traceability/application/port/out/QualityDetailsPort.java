package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QualityDetailsPort {

    void saveTests(UUID batchId, Instant occurredAt, List<QualityTestValue> tests);

    void saveFailureCause(UUID batchId, Instant occurredAt, String cause);
}
