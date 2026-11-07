package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un lote tal como lo ve quien consulta. Siempre salen los campos siempre compartidos; los configurables
 * ({@code supplierName}, {@code origin}, {@code quantityKg}, {@code composition}, {@code receptionCharacteristics}) solo si es de la
 * propia empresa o la dueña los compartió; {@code failureCause} y {@code qualityTests}, igual con {@code FAILURE_CAUSE} y {@code QUALITY_TEST_VALUES};
 * {@code qualityHold} y {@code name} (el nombre libre del lote) solo los ve la empresa dueña; {@code transferHistory} solo en la vista completa.
 * Solo lo construye {@code LineageAuthorizationPolicy}.
 */
public record VisibleBatch(UUID id, NodeKind kind, int level, Access access, UUID companyId, String businessName, String traceabilityId,
                           String qrCode, String materialType, BatchPhase phase, QualityStatus qualityStatus, Instant createdAt,
                           boolean finalProduct, String supplierName, String origin, BigDecimal quantityKg, List<CompositionComponent> composition,
                           String receptionCharacteristics, Boolean qualityHold, List<TransferEntry> transferHistory,
                           String failureCause, List<VisibleQualityTest> qualityTests, String name) implements VisibleNode {
}
