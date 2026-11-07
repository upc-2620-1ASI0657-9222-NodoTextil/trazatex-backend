package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import com.nodotextil.trazatex.traceability.application.port.out.ImpactEventPort;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * El resultado del análisis de impacto va al outbox de Neo4j (ADR-004), en la misma transacción que el {@code :ProcessedEvent} del {@code FailureConfirmed} que lo
 * originó: o quedan los dos o ninguno. El relay lo publica en {@code traceability.impact} con el {@code failureId} como clave.
 */
@Component
class OutboxImpactEventAdapter implements ImpactEventPort {

    private final OutboxEventRecorder recorder;
    private final Neo4jClient neo4j;
    private final Clock clock;

    OutboxImpactEventAdapter(OutboxEventRecorder recorder, Neo4jClient neo4j, Clock clock) {
        this.recorder = recorder;
        this.neo4j = neo4j;
        this.clock = clock;
    }

    @Override
    public boolean alreadyRecorded(UUID failureId) {
        return (Long) neo4j.query("MATCH (o:OutboxEvent {aggregateId: $failure, eventType: $type}) RETURN count(o) AS n")
                .bind(failureId.toString()).to("failure").bind(ImpactEvents.POSSIBLE_DERIVED_FAILURE_DETECTED).to("type")
                .fetch().one().orElseThrow().get("n") > 0;
    }

    @Override
    public void record(UUID failureId, ImpactResult result) {
        ImpactEvents.PossibleDerivedFailureDetected payload = new ImpactEvents.PossibleDerivedFailureDetected(failureId, result.sourceBatchId(),
                result.affected().stream().map(a -> new ImpactEvents.Affected(a.batchId(), a.companyId(), a.commonAncestorBatchId())).toList());
        recorder.record(ImpactEvents.AGGREGATE_TYPE, ProducedTopics.IMPACT,
                BaseDomainEvent.of(ImpactEvents.POSSIBLE_DERIVED_FAILURE_DETECTED, failureId.toString(), payload, clock));
    }
}
