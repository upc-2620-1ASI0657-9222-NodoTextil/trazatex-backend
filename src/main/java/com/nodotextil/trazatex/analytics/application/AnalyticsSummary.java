package com.nodotextil.trazatex.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record AnalyticsSummary(
        UUID companyId,
        LocalDate from,
        LocalDate to,
        QualityIndicators quality,
        FailureIndicators failures,
        OperationalTimeIndicators operationalTimes,
        MaterialLossIndicators materialLosses) {

    public record QualityIndicators(
            long totalLots,
            Map<String, Long> countsByStatus,
            Map<String, BigDecimal> percentagesByStatus) {
    }

    public record FailureIndicators(
            long totalFailures,
            Map<String, Long> causes) {
    }

    public record OperationalTimeIndicators(
            BigDecimal averageTransformationMinutes,
            BigDecimal averageTransferMinutes,
            BigDecimal averageQualityControlMinutes) {
    }

    public record MaterialLossIndicators(
            BigDecimal totalShrinkageKg,
            BigDecimal totalWasteKg) {
    }
}
