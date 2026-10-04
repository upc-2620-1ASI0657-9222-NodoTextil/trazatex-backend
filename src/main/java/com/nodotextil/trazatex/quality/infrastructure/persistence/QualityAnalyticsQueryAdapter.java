package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.contract.QualityAnalyticsQuery;
import com.nodotextil.trazatex.quality.domain.QualityStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class QualityAnalyticsQueryAdapter implements QualityAnalyticsQuery {

    private final SpringDataBatchQualityRepository batchQualities;
    private final SpringDataFailureRepository failures;
    private final SpringDataQualityControlRepository controls;

    public QualityAnalyticsQueryAdapter(
            SpringDataBatchQualityRepository batchQualities,
            SpringDataFailureRepository failures,
            SpringDataQualityControlRepository controls) {
        this.batchQualities = batchQualities;
        this.failures = failures;
        this.controls = controls;
    }

    @Override
    public QualityMetrics summarize(
            Set<UUID> allCompanyBatchIds,
            Set<UUID> registeredBatchIds,
            LocalDateTime from,
            LocalDateTime toExclusive) {
        Map<UUID, QualityStatus> statusesByBatch = batchQualities.findAllById(registeredBatchIds).stream()
                .map(BatchQualityJpaEntity::toDomain)
                .collect(Collectors.toMap(value -> value.getBatchId(), value -> value.getStatus()));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Arrays.stream(QualityStatus.values()).forEach(status -> statusCounts.put(status.name(), 0L));
        registeredBatchIds.forEach(batchId -> {
            QualityStatus status = statusesByBatch.getOrDefault(batchId, QualityStatus.NOT_REVIEWED);
            statusCounts.compute(status.name(), (key, count) -> count + 1);
        });

        List<FailureJpaEntity> periodFailures = failures.findAll().stream()
                .filter(failure -> allCompanyBatchIds.contains(failure.toDomain().getBatchId()))
                .filter(failure -> within(failure.toDomain().getCreatedAt(), from, toExclusive))
                .toList();
        Map<String, Long> failureCauses = periodFailures.stream()
                .map(FailureJpaEntity::toDomain)
                .map(value -> value.getCause() == null || value.getCause().isBlank() ? "UNSPECIFIED" : value.getCause().trim())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> first,
                        LinkedHashMap::new));

        List<Duration> controlDurations = controls.findAll().stream()
                .map(QualityControlJpaEntity::toDomain)
                .filter(control -> allCompanyBatchIds.contains(control.getBatchId()))
                .filter(control -> within(control.getCompletedAt(), from, toExclusive))
                .map(control -> Duration.between(control.getStartedAt(), control.getCompletedAt()))
                .toList();

        return new QualityMetrics(
                Map.copyOf(statusCounts),
                periodFailures.size(),
                Map.copyOf(failureCauses),
                averageMinutes(controlDurations));
    }

    private static boolean within(LocalDateTime value, LocalDateTime from, LocalDateTime toExclusive) {
        return value != null && !value.isBefore(from) && value.isBefore(toExclusive);
    }

    private static BigDecimal averageMinutes(List<Duration> durations) {
        if (durations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long totalSeconds = durations.stream().mapToLong(Duration::getSeconds).sum();
        return BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(60L * durations.size()), 2, RoundingMode.HALF_UP);
    }
}
