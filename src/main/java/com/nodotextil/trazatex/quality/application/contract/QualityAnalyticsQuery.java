package com.nodotextil.trazatex.quality.application.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface QualityAnalyticsQuery {

    QualityMetrics summarize(
            Set<UUID> allCompanyBatchIds,
            Set<UUID> registeredBatchIds,
            LocalDateTime from,
            LocalDateTime toExclusive);

    record QualityMetrics(
            Map<String, Long> statusCounts,
            long failureCount,
            Map<String, Long> failureCauses,
            BigDecimal averageQualityControlMinutes) {
    }
}
