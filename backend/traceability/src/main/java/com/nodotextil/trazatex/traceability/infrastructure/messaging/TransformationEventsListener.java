package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.EventFields;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.nodotextil.trazatex.traceability.infrastructure.messaging.EventPayloads.parts;

/** {@code production.transformation}: {@code TransformationCompleted}. */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class TransformationEventsListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    TransformationEventsListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper,
                                 ApplyTransformationCompletedUseCase transformation) {
        super(handler, retry, objectMapper);
        this.effects = Map.of("TransformationCompleted", e -> {
            EventPayloads.TransformationCompleted p = payload(e, EventPayloads.TransformationCompleted.class);
            List<UUID> inputs = p.inputs() == null ? null : p.inputs().stream().map(EventPayloads.TransformationInput::batchId).toList();
            List<ApplyTransformationCompletedCommand.Output> outputs = p.outputs() == null ? null : p.outputs().stream().map(o -> {
                if (o.finalProduct() == null) {
                    throw EventFields.invalid("outputs.finalProduct", "falta");
                }
                return new ApplyTransformationCompletedCommand.Output(o.batchId(), o.traceabilityId(), o.qrCode(), o.materialType(), o.quantityKg(),
                        parts(o.composition()), o.finalProduct(), o.name());
            }).toList();
            transformation.apply(new ApplyTransformationCompletedCommand(e.eventId(), p.transformationId(), p.type(), p.companyId(), p.machineName(),
                    p.machineType(), inputs, outputs, p.wasteKg(), p.wasteReason(), p.shrinkageKg(), p.startedAt(), p.completedAt()));
        });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.TRANSFORMATION_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_TRANSFORMATION, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.transformation.group:traceability.production-transformation}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}
