package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.quality.application.dto.OutputBatch;
import com.nodotextil.trazatex.quality.application.port.in.SyncBatchProjectionUseCase;
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

/** {@code production.transformation}: {@code TransformationCompleted} da a conocer las salidas (no llevan {@code BatchRegistered}). */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TransformationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(TransformationEventsListener.class);

    static final String TRANSFORMATION_COMPLETED = "TransformationCompleted";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompletedPayload(UUID companyId, List<OutputPayload> outputs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputPayload(UUID batchId, String traceabilityId) {
    }

    private final IdempotentEventHandler handler;
    private final SyncBatchProjectionUseCase projection;
    private final ObjectMapper objectMapper;

    TransformationEventsListener(IdempotentEventHandler handler, SyncBatchProjectionUseCase projection, ObjectMapper objectMapper) {
        this.handler = handler;
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = ConsumedTopics.TRANSFORMATION_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_TRANSFORMATION, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.transformation.group:quality.production-transformation}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        if (!TRANSFORMATION_COMPLETED.equals(event.type())) {
            log.debug("Evento {} ({}) de {} no es de quality: se ignora", event.eventId(), event.type(), record.topic());
            return;
        }
        handler.handle(event, e -> {
            CompletedPayload p = EventEnvelopes.payload(objectMapper, e, CompletedPayload.class);
            require(e, p.companyId() != null && p.outputs() != null && !p.outputs().isEmpty()
                    && p.outputs().stream().allMatch(o -> o != null && o.batchId() != null), "companyId y outputs[{batchId}]");
            projection.transformationOutputs(p.companyId(), p.outputs().stream().map(o -> new OutputBatch(o.batchId(), o.traceabilityId())).toList());
        });
    }
}
