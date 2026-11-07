package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.quality.application.dto.AffectedBatch;
import com.nodotextil.trazatex.quality.application.port.in.RecordDerivedFailureUseCase;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.quality.infrastructure.messaging.ProductionEventListeners.require;

/**
 * {@code traceability.impact}: {@code PossibleDerivedFailureDetected} (clave {@code failureId}). Solo registra los avisos; la retención ({@code HOLD})
 * la manda un job (ver {@code RecordDerivedFailureUseCase}). Lo demás que viaje por el tópico se ignora antes del consumo idempotente.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class ImpactEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ImpactEventsListener.class);

    static final String POSSIBLE_DERIVED_FAILURE_DETECTED = "PossibleDerivedFailureDetected";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetectedPayload(UUID failureId, UUID sourceBatchId, List<AffectedPayload> affected) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AffectedPayload(UUID batchId, UUID companyId, UUID commonAncestorBatchId) {
    }

    private final IdempotentEventHandler handler;
    private final RecordDerivedFailureUseCase derivedFailures;
    private final ObjectMapper objectMapper;

    ImpactEventsListener(IdempotentEventHandler handler, RecordDerivedFailureUseCase derivedFailures, ObjectMapper objectMapper) {
        this.handler = handler;
        this.derivedFailures = derivedFailures;
        this.objectMapper = objectMapper;
    }

    /** {@code autoStartup=false}: lo arranca el vigilante cuando el tópico (de Traceability) existe. */
    @KafkaListener(id = ConsumedTopics.IMPACT_LISTENER_ID, topics = ConsumedTopics.TRACEABILITY_IMPACT, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.impact.group:quality.traceability-impact}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        if (!POSSIBLE_DERIVED_FAILURE_DETECTED.equals(event.type())) {
            log.debug("Evento {} ({}) de {} no es de quality: se ignora", event.eventId(), event.type(), record.topic());
            return;
        }
        handler.handle(event, e -> {
            DetectedPayload p = EventEnvelopes.payload(objectMapper, e, DetectedPayload.class);
            require(e, p.failureId() != null && p.sourceBatchId() != null && p.affected() != null && !p.affected().isEmpty()
                    && p.affected().stream().allMatch(a -> a != null && a.batchId() != null && a.commonAncestorBatchId() != null),
                    "failureId, sourceBatchId y affected[{batchId, commonAncestorBatchId}]");
            try {
                derivedFailures.record(p.failureId(), p.sourceBatchId(),
                        p.affected().stream().map(a -> new AffectedBatch(a.batchId(), a.companyId(), a.commonAncestorBatchId())).toList());
            } catch (TrazatexException invalid) {
                throw new MalformedEventException("El payload de " + e.type() + " (" + e.eventId() + ") no es válido: " + invalid.getMessage(), invalid);
            }
        });
    }
}
