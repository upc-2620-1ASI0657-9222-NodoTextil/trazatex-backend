package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.TransformationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una transformación tal como la ve quien consulta: siempre {@code type}, {@code startedAt}, {@code completedAt} y la empresa; la máquina
 * ({@code MACHINERY}), la merma, el desperdicio y su motivo solo si es de la propia empresa o la dueña los compartió.
 */
public record VisibleTransformation(UUID id, NodeKind kind, int level, Access access, UUID companyId, String businessName, TransformationType type,
                                    Instant startedAt, Instant completedAt, String machineName, String machineType, BigDecimal shrinkageKg,
                                    BigDecimal wasteKg, String wasteReason) implements VisibleNode {
}
