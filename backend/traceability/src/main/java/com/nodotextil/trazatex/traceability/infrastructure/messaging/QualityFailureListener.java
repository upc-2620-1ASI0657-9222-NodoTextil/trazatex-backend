package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.port.in.AnalyzeFailureImpactUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code quality.failure}: {@code FailureConfirmed} (clave {@code failureId}) dispara el análisis de impacto (US33). El tópico es de Quality (siguiente sprint) y puede
 * no existir todavía: este consumidor no lo crea y empieza a leerlo cuando aparezca ({@link TopicWatcherJob}). Si la familia del lote fallado aún no está completa, el
 * caso de uso lanza una excepción reintentable y el contenedor reintenta con su espera propia (ver {@code MessagingConfig}); agotada, el mensaje va a
 * {@code quality.failure.DLT} con un aviso (ver {@link ImpactDeadLetterReporter}).
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class QualityFailureListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    QualityFailureListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper, AnalyzeFailureImpactUseCase impact) {
        super(handler, retry, objectMapper);
        this.effects = Map.of("FailureConfirmed", e -> {
            EventPayloads.FailureConfirmed p = payload(e, EventPayloads.FailureConfirmed.class);
            impact.apply(new AnalyzeFailureImpactCommand(e.eventId(), p.failureId(), p.batchId()));
        });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.FAILURE_LISTENER_ID, topics = ConsumedTopics.QUALITY_FAILURE, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.failure.group:traceability.quality-failure}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}
