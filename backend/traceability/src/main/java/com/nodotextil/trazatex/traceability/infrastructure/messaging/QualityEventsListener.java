package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityStatusCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code quality.control}: {@code QualityStatusChanged} (clave {@code batchId}: el orden de un lote se conserva). El tópico es de
 * Quality (siguiente sprint) y puede no existir todavía: este consumidor no lo crea y empieza a leerlo cuando aparezca
 * ({@link TopicWatcherJob}). {@code QualityControlCompleted}, que va a analytics, se ignora.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class QualityEventsListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    QualityEventsListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper, ApplyQualityStatusUseCase quality) {
        super(handler, retry, objectMapper);
        this.effects = Map.of("QualityStatusChanged", e -> {
            EventPayloads.QualityStatusChanged p = payload(e, EventPayloads.QualityStatusChanged.class);
            quality.apply(new ApplyQualityStatusCommand(e.eventId(), p.batchId(), p.newStatus()));
        });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.QUALITY_LISTENER_ID, topics = ConsumedTopics.QUALITY_CONTROL, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.quality.group:traceability.quality-control}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}
