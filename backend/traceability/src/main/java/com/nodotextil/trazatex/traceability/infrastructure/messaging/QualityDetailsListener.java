package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityDetailsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Los detalles de calidad que se muestran en la genealogía: los valores de las pruebas ({@code QualityControlCompleted} en {@code quality.control}) y la causa de
 * falla ({@code QualityFailureUpdated} en {@code quality.failure}). Cada tópico con su propio grupo, para no interferir con los consumidores de estado e impacto.
 * Traduce el mensaje a un comando y llama al puerto de entrada; lo demás se ignora.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class QualityDetailsListener {

    static final String QUALITY_CONTROL_COMPLETED = "QualityControlCompleted";
    static final String QUALITY_FAILURE_UPDATED = "QualityFailureUpdated";
    private static final TypeReference<List<ApplyQualityTestsCommand.TestValue>> TESTS = new TypeReference<>() {
    };

    private final IdempotentEventHandler handler;
    private final ObjectMapper mapper;
    private final ApplyQualityDetailsUseCase details;

    QualityDetailsListener(IdempotentEventHandler handler, ObjectMapper mapper, ApplyQualityDetailsUseCase details) {
        this.handler = handler;
        this.mapper = mapper;
        this.details = details;
    }

    @KafkaListener(id = "traceability-quality-tests", topics = "quality.control", groupId = "traceability.quality-tests", autoStartup = "false")
    void tests(String json) {
        BaseDomainEvent event = EventEnvelopes.parse(mapper, json);
        if (!QUALITY_CONTROL_COMPLETED.equals(event.type())) {
            return;
        }
        JsonNode payload = mapper.valueToTree(event.payload());
        if (!payload.path("tests").isArray()) {
            return;
        }
        handler.handle(event, e -> details.applyTests(new ApplyQualityTestsCommand(e.eventId(), batchId(payload), e.occurredAt(),
                mapper.convertValue(payload.path("tests"), TESTS))));
    }

    @KafkaListener(id = "traceability-failure-details", topics = "quality.failure", groupId = "traceability.failure-details", autoStartup = "false")
    void failures(String json) {
        BaseDomainEvent event = EventEnvelopes.parse(mapper, json);
        if (!QUALITY_FAILURE_UPDATED.equals(event.type())) {
            return;
        }
        JsonNode payload = mapper.valueToTree(event.payload());
        handler.handle(event, e -> details.applyFailureCause(new ApplyFailureCauseCommand(e.eventId(), batchId(payload), e.occurredAt(),
                payload.path("status").asText(""), payload.path("cause").asText(""))));
    }

    private static UUID batchId(JsonNode payload) {
        return UUID.fromString(payload.path("batchId").asText());
    }
}
