package com.nodotextil.trazatex.analytics.application;

import com.nodotextil.trazatex.production.application.contract.ProductionAnalyticsQuery;
import com.nodotextil.trazatex.quality.application.contract.QualityAnalyticsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsSummaryService {

    private final ProductionAnalyticsQuery productionAnalytics;
    private final QualityAnalyticsQuery qualityAnalytics;

    public AnalyticsSummaryService(
            ProductionAnalyticsQuery productionAnalytics,
            QualityAnalyticsQuery qualityAnalytics) {
        this.productionAnalytics = productionAnalytics;
        this.qualityAnalytics = qualityAnalytics;
    }

    public AnalyticsSummary summarize(UUID companyId, LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(30) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("The start date cannot be after the end date");
        }

        LocalDateTime fromDateTime = resolvedFrom.atStartOfDay();
        LocalDateTime toExclusive = resolvedTo.plusDays(1).atStartOfDay();
        var production = productionAnalytics.summarize(companyId, fromDateTime, toExclusive);
        var quality = qualityAnalytics.summarize(
                production.allBatchIds(),
                production.registeredBatchIds(),
                fromDateTime,
                toExclusive);

        long totalLots = quality.statusCounts().values().stream().mapToLong(Long::longValue).sum();
        Map<String, BigDecimal> percentages = new LinkedHashMap<>();
        quality.statusCounts().forEach((status, count) -> percentages.put(
                status,
                totalLots == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(count * 100L)
                                .divide(BigDecimal.valueOf(totalLots), 2, RoundingMode.HALF_UP)));

        return new AnalyticsSummary(
                companyId,
                resolvedFrom,
                resolvedTo,
                new AnalyticsSummary.QualityIndicators(totalLots, quality.statusCounts(), Map.copyOf(percentages)),
                new AnalyticsSummary.FailureIndicators(quality.failureCount(), quality.failureCauses()),
                new AnalyticsSummary.OperationalTimeIndicators(
                        production.averageTransformationMinutes(),
                        production.averageTransferMinutes(),
                        quality.averageQualityControlMinutes()),
                new AnalyticsSummary.MaterialLossIndicators(
                        production.totalShrinkageKg(),
                        production.totalWasteKg()));
    }
}
