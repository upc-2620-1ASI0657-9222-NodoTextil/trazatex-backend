package com.nodotextil.trazatex.traceability.domain.model.view;

import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;
import com.nodotextil.trazatex.traceability.domain.model.QualityStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VisibleBatch(UUID id, NodeKind kind, int level, Access access, UUID companyId, String businessName, String traceabilityId,
                           String qrCode, String materialType, BatchPhase phase, QualityStatus qualityStatus, Instant createdAt,
                           boolean finalProduct, String supplierName, String origin, BigDecimal quantityKg, List<CompositionComponent> composition,
                           String receptionCharacteristics, Boolean qualityHold, List<TransferEntry> transferHistory,
                           String failureCause, List<VisibleQualityTest> qualityTests, String name) implements VisibleNode {
}
