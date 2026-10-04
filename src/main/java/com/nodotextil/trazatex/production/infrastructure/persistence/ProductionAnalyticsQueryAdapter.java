package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.application.contract.ProductionAnalyticsQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ProductionAnalyticsQueryAdapter implements ProductionAnalyticsQuery {

    private final SpringDataBatchJpaRepository batches;
    private final SpringDataTransformationJpaRepository transformations;
    private final SpringDataTransferJpaRepository transfers;

    public ProductionAnalyticsQueryAdapter(
            SpringDataBatchJpaRepository batches,
            SpringDataTransformationJpaRepository transformations,
            SpringDataTransferJpaRepository transfers) {
        this.batches = batches;
        this.transformations = transformations;
        this.transfers = transfers;
    }

    @Override
    public ProductionMetrics summarize(UUID companyId, LocalDateTime from, LocalDateTime toExclusive) {
        Set<UUID> allBatchIds = new LinkedHashSet<>();
        Set<UUID> registeredBatchIds = new LinkedHashSet<>();
        batches.findAll().stream()
                .filter(batch -> companyId.equals(batch.getResponsibleCompanyId()))
                .forEach(batch -> {
                    allBatchIds.add(batch.getId());
                    if (within(batch.getRegisteredAt(), from, toExclusive)) {
                        registeredBatchIds.add(batch.getId());
                    }
                });

        List<TransformationJpaEntity> completedTransformations = transformations.findAll().stream()
                .filter(transformation -> companyId.equals(transformation.getCompanyId()))
                .filter(transformation -> within(transformation.getCompletedAt(), from, toExclusive))
                .toList();

        List<TransferJpaEntity> completedTransfers = transfers.findAll().stream()
                .filter(transfer -> companyId.equals(transfer.getSourceCompanyId())
                        || companyId.equals(transfer.getDestinationCompanyId()))
                .filter(transfer -> within(transfer.getCompletedAt(), from, toExclusive))
                .toList();

        BigDecimal totalShrinkage = completedTransformations.stream()
                .map(TransformationJpaEntity::getShrinkageKg)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWaste = completedTransformations.stream()
                .map(TransformationJpaEntity::getWasteKg)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductionMetrics(
                Set.copyOf(allBatchIds),
                Set.copyOf(registeredBatchIds),
                averageMinutes(completedTransformations.stream()
                        .map(value -> Duration.between(value.getStartedAt(), value.getCompletedAt()))
                        .toList()),
                averageMinutes(completedTransfers.stream()
                        .map(value -> Duration.between(value.getStartedAt(), value.getCompletedAt()))
                        .toList()),
                totalShrinkage,
                totalWaste);
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
