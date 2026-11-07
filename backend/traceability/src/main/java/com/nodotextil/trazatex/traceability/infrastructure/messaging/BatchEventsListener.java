package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.nodotextil.trazatex.traceability.infrastructure.messaging.EventPayloads.parts;

/**
 * {@code production.batch}: {@code BatchRegistered}, {@code BatchSplit} y {@code BatchPhaseChanged}. Los lotes de distintas
 * claves de este tópico y los de los demás tópicos llegan sin orden entre sí; el grafo lo resuelve (ver {@code LineageGraphPort}).
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class BatchEventsListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    BatchEventsListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper, RegisterBatchUseCase register,
                        ApplyBatchSplitUseCase split, ApplyBatchPhaseChangedUseCase phaseChanged) {
        super(handler, retry, objectMapper);
        this.effects = Map.of(
                "BatchRegistered", e -> {
                    EventPayloads.BatchRegistered p = payload(e, EventPayloads.BatchRegistered.class);
                    register.apply(new RegisterBatchCommand(e.eventId(), p.batchId(), p.traceabilityId(), p.qrCode(), p.companyId(), p.materialType(),
                            parts(p.composition()), p.quantityKg(), p.origin(), p.supplierName(), p.receptionCharacteristics(), p.createdAt(), p.name()));
                },
                "BatchSplit", e -> {
                    EventPayloads.BatchSplit p = payload(e, EventPayloads.BatchSplit.class);
                    List<ApplyBatchSplitCommand.Child> children = p.children() == null ? null : p.children().stream()
                            .map(c -> new ApplyBatchSplitCommand.Child(c.batchId(), c.traceabilityId(), c.qrCode(), c.materialType(), c.quantityKg(),
                                    parts(c.composition()), c.origin(), c.supplierName(), c.receptionCharacteristics(), c.qualityStatus(), c.createdAt(), c.name()))
                            .toList();
                    split.apply(new ApplyBatchSplitCommand(e.eventId(), p.parentBatchId(), p.companyId(), children));
                },
                "BatchPhaseChanged", e -> {
                    EventPayloads.BatchPhaseChanged p = payload(e, EventPayloads.BatchPhaseChanged.class);
                    if (p.qualityHold() == null) {
                        throw com.nodotextil.trazatex.traceability.application.dto.EventFields.invalid("qualityHold", "falta");
                    }
                    phaseChanged.apply(new ApplyBatchPhaseChangedCommand(e.eventId(), p.batchId(), p.companyId(), p.newPhase(), p.qualityHold(), p.batchVersion()));
                });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.BATCH_LISTENER_ID, topics = ConsumedTopics.PRODUCTION_BATCH, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.batch.group:traceability.production-batch}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}
