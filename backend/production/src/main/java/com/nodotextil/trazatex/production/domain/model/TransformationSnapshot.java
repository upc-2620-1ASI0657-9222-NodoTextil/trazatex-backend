package com.nodotextil.trazatex.production.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Todos los datos de una transformación, para reconstruirla con {@code TransformationFactory.restore}. */
public record TransformationSnapshot(
        UUID id,
        UUID companyId,
        TransformationType type,
        UUID machineId,
        UUID operatorId,
        TransformationStatus status,
        List<TransformationInput> inputs,
        List<UUID> outputBatchIds,
        Quantity totalInput,
        Quantity totalOutput,
        Quantity waste,
        String wasteReason,
        Quantity shrinkage,
        Instant startTime,
        Instant endTime) {
}
