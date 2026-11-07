package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.quality.application.dto.SplitChild;
import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;
import com.nodotextil.trazatex.quality.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
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
 * {@code production.batch}: {@code BatchRegistered}, {@code BatchSplit} y {@code BatchPhaseChanged} alimentan {@code batch_projection}.
 * Lo demás que viaje por el tópico se ignora sin error y sin DLT; como se filtra <b>antes</b> del consumo idempotente tampoco deja
 * filas en {@code processed_events}. Lo ilegible sí va a la DLT sin reintentos.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class BatchEventsListener {

    private static final Logger log = LoggerFactory.getLogger(BatchEventsListener.class);

    static final String BATCH_REGISTERED = "BatchRegistered";
    static final String BATCH_SPLIT = "BatchSplit";
    static final String BATCH_PHASE_CHANGED = "BatchPhaseChanged";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegisteredPayload(UUID batchId, UUID companyId, String traceabilityId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SplitPayload(UUID parentBatchId, UUID companyId, List<ChildPayload> children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChildPayload(UUID batchId, QualityStatus qualityStatus, String traceabilityId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhaseChangedPayload(UUID batchId, UUID companyId, BatchPhase newPhase, Boolean qualityHold, Long batchVersion) {
    }

    private final IdempotentEventHandler handler;
    private final SyncBatchProjectionUseCase projection;
    private final ObjectMapper objectMapper;

    BatchEventsListener(IdempotentEventHandler handler, SyncBatchProjectionUseCase projection, ObjectMapper objectMapper) {
        this.handler = handler;
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    /** {@code autoStartup=false}: lo arranca el vigilante cuando el tópico existe (suscribirse a uno ausente inunda el log). */
    @KafkaListener(id = ConsumedTopics.BATCH_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_BATCH, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.batch.group:quality.production-batch}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        switch (event.type()) {
            case BATCH_REGISTERED -> handler.handle(event, e -> {
                RegisteredPayload p = EventEnvelopes.payload(objectMapper, e, RegisteredPayload.class);
                require(e, p.batchId() != null && p.companyId() != null, "batchId y companyId");
                projection.registered(p.batchId(), p.companyId(), p.traceabilityId());
            });
            case BATCH_SPLIT -> handler.handle(event, e -> {
                SplitPayload p = EventEnvelopes.payload(objectMapper, e, SplitPayload.class);
                require(e, p.companyId() != null && p.children() != null && !p.children().isEmpty()
                        && p.children().stream().allMatch(c -> c != null && c.batchId() != null && c.qualityStatus() != null),
                        "companyId y children[{batchId, qualityStatus}]");
                projection.split(p.companyId(), p.children().stream().map(c -> new SplitChild(c.batchId(), c.qualityStatus(), c.traceabilityId())).toList());
            });
            case BATCH_PHASE_CHANGED -> handler.handle(event, e -> {
                PhaseChangedPayload p = EventEnvelopes.payload(objectMapper, e, PhaseChangedPayload.class);
                require(e, p.batchId() != null && p.companyId() != null && p.newPhase() != null && p.qualityHold() != null && p.batchVersion() != null,
                        "batchId, companyId, newPhase, qualityHold y batchVersion");
                projection.phaseChanged(p.batchId(), p.companyId(), p.newPhase(), p.qualityHold(), p.batchVersion());
            });
            default -> log.debug("Evento {} ({}) de {} no es de quality: se ignora", event.eventId(), event.type(), record.topic());
        }
    }
}
