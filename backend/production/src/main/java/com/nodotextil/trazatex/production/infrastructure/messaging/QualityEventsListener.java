package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class QualityEventsListener {

    private static final Logger log = LoggerFactory.getLogger(QualityEventsListener.class);

    static final String QUALITY_STATUS_CHANGED = "QualityStatusChanged";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QualityStatusChangedPayload(UUID batchId, UUID companyId, QualityStatus newStatus, UUID controlId) {
    }

    private final IdempotentEventHandler handler;
    private final ApplyQualityStatusUseCase qualityStatus;
    private final ObjectMapper objectMapper;

    QualityEventsListener(IdempotentEventHandler handler, ApplyQualityStatusUseCase qualityStatus, ObjectMapper objectMapper) {
        this.handler = handler;
        this.qualityStatus = qualityStatus;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = ConsumedTopics.QUALITY_LISTENER_ID, topics = ConsumedTopics.QUALITY_CONTROL, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.quality.group:production.quality-control}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        if (!QUALITY_STATUS_CHANGED.equals(event.type())) {
            log.debug("Evento {} ({}) de {} no es de production: se ignora", event.eventId(), event.type(), record.topic());
            return;
        }
        try {
            handler.handle(event, e -> {
                QualityStatusChangedPayload payload = EventEnvelopes.payload(objectMapper, e, QualityStatusChangedPayload.class);
                CompanyEventsListener.require(e, payload.batchId() != null && payload.companyId() != null && payload.newStatus() != null,
                        "batchId, companyId y newStatus");
                qualityStatus.apply(payload.batchId(), payload.companyId(), payload.newStatus(), payload.controlId());
            });
        } catch (NotFoundException e) {
            log.error("QualityStatusChanged {} referencia un lote que no existe ({}): va a la DLT sin reintentos", event.eventId(), e.getMessage());
            throw new NonRetryableEventException(e.getMessage(), e);
        }
    }
}
