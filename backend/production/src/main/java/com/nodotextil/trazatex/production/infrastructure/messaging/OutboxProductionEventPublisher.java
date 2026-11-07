package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.event.BatchPhaseChanged;
import com.nodotextil.trazatex.production.domain.event.BatchRegistered;
import com.nodotextil.trazatex.production.domain.event.BatchSplit;
import com.nodotextil.trazatex.production.domain.event.ProductionEvent;
import com.nodotextil.trazatex.production.domain.event.TransferAccepted;
import com.nodotextil.trazatex.production.domain.event.TransferInitiated;
import com.nodotextil.trazatex.production.domain.event.TransferRejected;
import com.nodotextil.trazatex.production.domain.event.TransformationCompleted;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Escribe el evento en el outbox, en la transacción del caso de uso (ADR-004). Tópicos del catálogo
 * ({@code docs/architecture/communication.md}); el relay los publica en Kafka tras el commit. Un evento sin ruta es un error
 * de programación y falla en el momento (con un {@code switch} sellado el compilador lo avisaría; la interfaz no lo es
 * para que los eventos de las fases siguientes no obliguen a tocar el dominio).
 */
@Component
class OutboxProductionEventPublisher implements ProductionEventPublisherPort {

    private final OutboxEventRecorder recorder;
    private final Clock clock;

    OutboxProductionEventPublisher(OutboxEventRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.clock = clock;
    }

    @Override
    public void publish(ProductionEvent event) {
        recorder.record(aggregateType(event), topic(event), BaseDomainEvent.of(event.eventType(), event.aggregateId(), event, clock));
    }

    private static String aggregateType(ProductionEvent event) {
        return switch (event) {
            case BatchRegistered e -> "Batch";
            case BatchSplit e -> "Batch";
            case BatchPhaseChanged e -> "Batch";
            case TransformationCompleted e -> "Transformation";
            case TransferInitiated e -> "Transfer";
            case TransferAccepted e -> "Transfer";
            case TransferRejected e -> "Transfer";
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.eventType());
        };
    }

    private static String topic(ProductionEvent event) {
        return switch (event) {
            case BatchRegistered e -> Topics.BATCH;
            case BatchSplit e -> Topics.BATCH;
            case BatchPhaseChanged e -> Topics.BATCH;
            case TransformationCompleted e -> Topics.TRANSFORMATION;
            case TransferInitiated e -> Topics.TRANSFER;
            case TransferAccepted e -> Topics.TRANSFER;
            case TransferRejected e -> Topics.TRANSFER;
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.eventType());
        };
    }
}
