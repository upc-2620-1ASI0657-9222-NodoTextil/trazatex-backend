package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

/** {@code production.transfer}: solo {@code TransferAccepted}, que cambia la empresa responsable de los lotes. */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TransferEventsListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventsListener.class);

    static final String TRANSFER_ACCEPTED = "TransferAccepted";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AcceptedPayload(List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId) {
    }

    private final IdempotentEventHandler handler;
    private final SyncBatchProjectionUseCase projection;
    private final ObjectMapper objectMapper;

    TransferEventsListener(IdempotentEventHandler handler, SyncBatchProjectionUseCase projection, ObjectMapper objectMapper) {
        this.handler = handler;
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = ConsumedTopics.TRANSFER_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_TRANSFER, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.transfer.group:quality.production-transfer}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        if (!TRANSFER_ACCEPTED.equals(event.type())) {
            log.debug("Evento {} ({}) de {} no es de quality: se ignora", event.eventId(), event.type(), record.topic());
            return;
        }
        handler.handle(event, e -> {
            AcceptedPayload p = EventEnvelopes.payload(objectMapper, e, AcceptedPayload.class);
            require(e, p.batchIds() != null && !p.batchIds().isEmpty() && p.fromCompanyId() != null && p.toCompanyId() != null,
                    "batchIds, fromCompanyId y toCompanyId");
            projection.transferAccepted(p.batchIds(), p.fromCompanyId(), p.toCompanyId());
        });
    }
}
