package com.nodotextil.trazatex.production.application.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public interface ProductionAnalyticsQuery {

    ProductionMetrics summarize(UUID companyId, LocalDateTime from, LocalDateTime toExclusive);

    record ProductionMetrics(
            Set<UUID> allBatchIds,
            Set<UUID> registeredBatchIds,
            BigDecimal averageTransformationMinutes,
            BigDecimal averageTransferMinutes,
            BigDecimal totalShrinkageKg,
            BigDecimal totalWasteKg) {
    }
}
