package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VisibleTransformation(UUID id, NodeKind kind, int level, Access access, UUID companyId, String businessName, TransformationType type,
                                    Instant startedAt, Instant completedAt, String machineName, String machineType, BigDecimal shrinkageKg,
                                    BigDecimal wasteKg, String wasteReason) implements VisibleNode {
}
