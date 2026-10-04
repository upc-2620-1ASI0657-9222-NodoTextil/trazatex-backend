package com.nodotextil.trazatex.traceability.application;

import com.nodotextil.trazatex.traceability.application.event.PossibleDerivedFailureDetectedEvent;
import com.nodotextil.trazatex.traceability.application.event.TraceabilityEventPublisher;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.application.port.LotInfo;
import com.nodotextil.trazatex.traceability.domain.AffectedLot;
import com.nodotextil.trazatex.traceability.domain.FailureImpactAnalyzer;
import com.nodotextil.trazatex.traceability.domain.LineageGraph;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyzeFailureImpactUseCase {

    private final LineageRepository lineageRepository;
    private final BatchInfoPort batchInfo;
    private final FailureImpactAnalyzer analyzer;
    private final TraceabilityEventPublisher eventPublisher;
    private final Clock clock;

    public AnalyzeFailureImpactUseCase(
            LineageRepository lineageRepository,
            BatchInfoPort batchInfo,
            FailureImpactAnalyzer analyzer,
            TraceabilityEventPublisher eventPublisher,
            Clock clock) {
        this.lineageRepository = lineageRepository;
        this.batchInfo = batchInfo;
        this.analyzer = analyzer;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Result execute(UUID failureId, UUID failedBatchId) {
        LineageGraph family = lineageRepository.findFamily(failedBatchId);
        Map<UUID, LotInfo> lots = batchInfo.findAllByIds(family.batchIds());

        List<AffectedLot> affected = analyzer.analyze(
                failedBatchId,
                family,
                batchId -> lots.containsKey(batchId) && lots.get(batchId).isAvailable());

        if (!affected.isEmpty()) {
            eventPublisher.publish(new PossibleDerivedFailureDetectedEvent(
                    failureId,
                    failedBatchId,
                    affected.stream().map(AffectedLot::batchId).toList(),
                    LocalDateTime.now(clock)));
        }
        return new Result(failureId, failedBatchId, affected);
    }

    public record Result(UUID failureId, UUID failedBatchId, List<AffectedLot> affectedLots) {
    }
}
