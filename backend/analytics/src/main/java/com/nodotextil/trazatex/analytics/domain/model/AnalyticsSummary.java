package com.nodotextil.trazatex.analytics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsSummary(LocalDate from, LocalDate to, String timeZone, List<QualityIndicator> quality,
                               List<FailureCause> failureCauses, List<OperationDuration> durations, MaterialLosses materials, Instant generatedAt) {

    public record QualityIndicator(String status, long count, BigDecimal percentage) { }

    public record FailureCause(String cause, long count) { }

    public record OperationDuration(String operation, long count, BigDecimal averageHours) { }

    public record MaterialLosses(BigDecimal shrinkageKg, BigDecimal wasteKg, long completedTransformations) { }

    public record Indicators(List<QualityIndicator> quality, List<FailureCause> failureCauses, List<OperationDuration> durations, MaterialLosses materials) {
        public AnalyticsSummary at(ReportingPeriod period, Instant generatedAt) {
            return new AnalyticsSummary(period.from(), period.to(), ReportingPeriod.ZONE.getId(), quality, failureCauses, durations, materials, generatedAt);
        }
    }
}
