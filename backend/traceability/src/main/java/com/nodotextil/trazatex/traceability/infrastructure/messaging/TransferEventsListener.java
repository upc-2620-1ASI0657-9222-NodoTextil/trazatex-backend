package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransferAcceptedCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Consumer;

/** {@code production.transfer}: solo {@code TransferAccepted}; {@code TransferInitiated} y {@code TransferRejected} son de notification. */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TransferEventsListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    TransferEventsListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper, ApplyTransferAcceptedUseCase transfer) {
        super(handler, retry, objectMapper);
        this.effects = Map.of("TransferAccepted", e -> {
            EventPayloads.TransferAccepted p = payload(e, EventPayloads.TransferAccepted.class);
            transfer.apply(new ApplyTransferAcceptedCommand(e.eventId(), p.transferId(), p.batchIds(), p.toCompanyId(), p.acceptedAt()));
        });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.TRANSFER_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_TRANSFER, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.transfer.group:traceability.production-transfer}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}
